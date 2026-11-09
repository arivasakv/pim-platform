package com.pim.backend.identifier.model;

/**
 * What an assignment request produced (FR-005, FR-009a).
 *
 * @param outcome a new ID, or the ID of the same object
 * @param registration the new or existing registration
 */
public record AssignmentResult(AssignmentOutcome outcome, SystemIdRegistration registration) {}
