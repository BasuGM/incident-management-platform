package com.example.incidentmanagement.organization.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = OrganizationSlugValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidOrganizationSlug {

    String message() default "Slug must be lowercase letters, numbers, and hyphens";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
