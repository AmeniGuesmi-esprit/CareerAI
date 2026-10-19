package com.careerai.interview.session;

import com.careerai.interview.config.InterviewConfig.InterviewProperties;
import com.careerai.interview.exception.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CompatAnalysisClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompatAnalysis(List<String> strengths, List<String> missingSkills, List<String> suggestions) { }

    private final RestTemplate restTemplate;
    private final InterviewProperties props;

    public CompatAnalysis fetch(Long userId, Long analysisId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        String url = props.compatServiceUrl() + "/api/compatibility/analyses/" + analysisId;
        try {
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), CompatAnalysis.class).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "Analyse de compatibilité introuvable : " + analysisId);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "COMPAT_UNAVAILABLE",
                    "Le service de compatibilité est injoignable.");
        }
    }
}
