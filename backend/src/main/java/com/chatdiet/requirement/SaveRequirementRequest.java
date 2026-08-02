package com.chatdiet.requirement;

/**
 * Request DTO for {@link SaveRequirementTool}.
 *
 * @param rawText the user's original wording of the request/complaint
 * @param summary a short summary to store alongside the raw text
 */
public record SaveRequirementRequest(String rawText, String summary) {
}
