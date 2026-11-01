package com.example.elibrary.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated user's id into a controller parameter.
 * <p>
 * The id is taken from the {@code X-User-Id} header, which the API gateway sets after
 * authenticating the caller (see README). It is never read from the body or the URL,
 * so a user cannot act on behalf of someone else by editing a request.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
