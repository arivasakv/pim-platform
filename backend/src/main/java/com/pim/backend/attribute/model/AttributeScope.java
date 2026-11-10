package com.pim.backend.attribute.model;

/**
 * Where an attribute's configuration applies (ERF 1.1 §3.2 ¶4). Only {@code GLOBAL} exists in this feature; local
 * (template-specific) scope arrives with the template feature (FR-018).
 */
public enum AttributeScope {
    GLOBAL
}
