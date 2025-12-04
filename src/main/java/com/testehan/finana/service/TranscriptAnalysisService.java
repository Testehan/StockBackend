package com.testehan.finana.service;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.filing.CompanyEarningsTranscripts;
import com.testehan.finana.model.filing.QuarterlyEarningsTranscript;
import com.testehan.finana.model.filing.Transcript;
import com.testehan.finana.repository.CompanyEarningsTranscriptsRepository;
import com.testehan.finana.repository.CompanyOverviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TranscriptAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptAnalysisService.class);

    private final CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository;
    private final CompanyOverviewRepository companyOverviewRepository;
    private final LlmService llmService;

    @Value("classpath:/prompts/qa/transcript_30_seconds_summary.txt")
    private Resource transcript30SecondsSummaryPrompt;

    @Value("classpath:/prompts/qa/transcript_buzzword_context_counter.txt")
    private Resource transcriptBuzzwordContextCounterPrompt;

    @Value("classpath:/prompts/qa/transcript_evasion_tracker.txt")
    private Resource transcriptEvasionTrackerPrompt;

    @Value("classpath:/prompts/qa/transcript_tone_shift_indicator.txt")
    private Resource transcriptToneShiftIndicatorPrompt;

    public TranscriptAnalysisService(CompanyEarningsTranscriptsRepository companyEarningsTranscriptsRepository,
                                     CompanyOverviewRepository companyOverviewRepository,
                                     LlmService llmService) {
        this.companyEarningsTranscriptsRepository = companyEarningsTranscriptsRepository;
        this.companyOverviewRepository = companyOverviewRepository;
        this.llmService = llmService;
    }

    public void analyzeTranscript(String stockId, String questionId, String quarter, SseEmitter emitter) {
        Resource promptResource = getPromptResource(questionId);
        if (promptResource == null) {
            sendError(emitter, "Unknown question type: " + questionId);
            return;
        }

        var transcriptData = getTranscriptData(stockId, quarter, emitter);
        if (transcriptData == null) return;

        QuarterlyEarningsTranscript targetTranscript = transcriptData.targetTranscript;

        if (hasExistingAnswer(targetTranscript, questionId)) {
            sendExistingAnswer(targetTranscript, questionId, stockId, emitter);
            return;
        }

        String companyName = getCompanyName(stockId);
        Map<String, Object> promptParameters = buildPromptParameters(questionId, targetTranscript, transcriptData.transcripts, companyName);

        executeLlmCall(promptResource, promptParameters, questionId, stockId, targetTranscript, transcriptData.companyTranscripts, emitter);
    }

    private Resource getPromptResource(String questionId) {
        switch (questionId) {
            case "transcript_30_seconds_summary": return transcript30SecondsSummaryPrompt;
            case "transcript_buzzword_context_counter": return transcriptBuzzwordContextCounterPrompt;
            case "transcript_evasion_tracker": return transcriptEvasionTrackerPrompt;
            case "transcript_tone_shift_indicator": return transcriptToneShiftIndicatorPrompt;
            default: return null;
        }
    }

    private TranscriptData getTranscriptData(String stockId, String quarter, SseEmitter emitter) {
        var companyTranscriptsOpt = companyEarningsTranscriptsRepository.findById(stockId.toUpperCase());
        if (companyTranscriptsOpt.isEmpty()) {
            sendError(emitter, "No transcripts found for stock: " + stockId);
            return null;
        }

        CompanyEarningsTranscripts companyTranscripts = companyTranscriptsOpt.get();
        List<QuarterlyEarningsTranscript> transcripts = companyTranscripts.getTranscripts();
        
        if (transcripts == null || transcripts.isEmpty()) {
            sendError(emitter, "No transcript data found for stock: " + stockId);
            return null;
        }

        transcripts.sort((a, b) -> b.getQuarter().compareTo(a.getQuarter()));

        QuarterlyEarningsTranscript targetTranscript = findTargetTranscript(transcripts, quarter);
        if (targetTranscript == null) {
            sendError(emitter, "No transcript found for quarter: " + quarter);
            return null;
        }

        return new TranscriptData(companyTranscripts, transcripts, targetTranscript);
    }

    private QuarterlyEarningsTranscript findTargetTranscript(List<QuarterlyEarningsTranscript> transcripts, String quarter) {
        if (quarter == null || quarter.isEmpty()) {
            return transcripts.get(0);
        }
        return transcripts.stream()
                .filter(t -> quarter.equals(t.getQuarter()))
                .findFirst()
                .orElse(null);
    }

    private boolean hasExistingAnswer(QuarterlyEarningsTranscript transcript, String questionId) {
        return transcript.getTranscriptAnalysisAnswers() != null 
                && transcript.getTranscriptAnalysisAnswers().containsKey(questionId);
    }

    private void sendExistingAnswer(QuarterlyEarningsTranscript transcript, String questionId, String stockId, SseEmitter emitter) {
        String existingAnswer = transcript.getTranscriptAnalysisAnswers().get(questionId);
        logger.info("Answer already exists for stockId: {}, questionId: {}, quarter: {}", stockId, questionId, transcript.getQuarter());
        sendAnswerAndComplete(emitter, existingAnswer);
    }

    private String getCompanyName(String stockId) {
        return companyOverviewRepository.findBySymbol(stockId.toUpperCase())
                .map(CompanyOverview::getCompanyName)
                .orElseGet(() -> {
                    logger.warn("No CompanyOverview found for stockId: {}, using ticker as company name", stockId);
                    return stockId.toUpperCase();
                });
    }

    private Map<String, Object> buildPromptParameters(String questionId, QuarterlyEarningsTranscript targetTranscript, 
                                                       List<QuarterlyEarningsTranscript> transcripts, String companyName) {
        Map<String, Object> params = new HashMap<>();
        params.put("company_name", companyName);

        String transcriptText = buildTranscriptText(targetTranscript.getTranscript());

        if ("transcript_tone_shift_indicator".equals(questionId)) {
            String pastTranscriptText = "";
            int targetIndex = transcripts.indexOf(targetTranscript);
            if (targetIndex > 0) {
                pastTranscriptText = buildTranscriptText(transcripts.get(targetIndex - 1).getTranscript());
            }
            params.put("transcript_current", transcriptText);
            params.put("transcript_past", pastTranscriptText);
        } else {
            params.put("transcript", transcriptText);
        }
        return params;
    }

    private void executeLlmCall(Resource promptResource, Map<String, Object> promptParameters, String questionId,
                                String stockId, QuarterlyEarningsTranscript targetTranscript, 
                                CompanyEarningsTranscripts companyTranscripts, SseEmitter emitter) {
        try {
            PromptTemplate promptTemplate = new PromptTemplate(promptResource);
            Prompt prompt = promptTemplate.create(promptParameters);
            StringBuilder completeAnswer = new StringBuilder();
            
            llmService.streamLlmWithSearch(prompt, questionId, stockId)
                    .doOnNext(chunk -> sendChunk(emitter, chunk, completeAnswer))
                    .doOnComplete(() -> saveAndComplete(questionId, stockId, targetTranscript, companyTranscripts, completeAnswer, emitter))
                    .doOnError(error -> handleError(emitter, error))
                    .subscribe();
        } catch (Exception e) {
            logger.error("Error during transcript analysis for stockId: {}, questionId: {}: {}", stockId, questionId, e.getMessage());
            sendError(emitter, e.getMessage());
        }
    }

    private void sendChunk(SseEmitter emitter, String chunk, StringBuilder completeAnswer) {
        try {
            emitter.send(SseEmitter.event().data(chunk));
            completeAnswer.append(chunk);
        } catch (java.io.IOException e) {
            logger.warn("Error sending SSE event", e);
        }
    }

    private void saveAndComplete(String questionId, String stockId, QuarterlyEarningsTranscript targetTranscript,
                                 CompanyEarningsTranscripts companyTranscripts, StringBuilder completeAnswer, SseEmitter emitter) {
        String answer = completeAnswer.toString();
        if (targetTranscript.getTranscriptAnalysisAnswers() == null) {
            targetTranscript.setTranscriptAnalysisAnswers(new HashMap<>());
        }
        targetTranscript.getTranscriptAnalysisAnswers().put(questionId, answer);
        companyEarningsTranscriptsRepository.save(companyTranscripts);

        logger.info("Completed transcript analysis for stockId: {}, questionId: {}, quarter: {}", stockId, questionId, targetTranscript.getQuarter());
        sendAnswerAndComplete(emitter, "");
    }

    private void sendAnswerAndComplete(SseEmitter emitter, String answer) {
        try {
            emitter.send(SseEmitter.event().data(answer));
            emitter.send(SseEmitter.event().name("COMPLETED").data("")); 
            emitter.complete();
        } catch (java.io.IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().data("Error: " + message));
            emitter.complete();
        } catch (java.io.IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void handleError(SseEmitter emitter, Throwable error) {
        logger.error("Error during transcript analysis streaming", error);
        emitter.completeWithError(error);
    }

    private String buildTranscriptText(List<Transcript> transcriptList) {
        if (transcriptList == null || transcriptList.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Transcript t : transcriptList) {
            sb.append(t.getSpeaker()).append(": ").append(t.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private record TranscriptData(CompanyEarningsTranscripts companyTranscripts, 
                                   List<QuarterlyEarningsTranscript> transcripts,
                                   QuarterlyEarningsTranscript targetTranscript) {}
}
