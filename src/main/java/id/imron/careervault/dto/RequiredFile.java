package id.imron.careervault.dto;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import org.springframework.web.multipart.MultipartFile;

/** Rejects a missing upload. A file input left blank binds as an empty file, so {@code @NotNull} is not enough. */
@Documented
@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = RequiredFile.Validator.class)
public @interface RequiredFile {

    String message() default "Choose a non-empty file.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<RequiredFile, MultipartFile> {

        @Override
        public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
            return file != null && !file.isEmpty();
        }
    }
}
