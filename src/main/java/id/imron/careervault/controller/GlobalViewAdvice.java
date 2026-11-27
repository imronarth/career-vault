package id.imron.careervault.controller;

import java.security.Principal;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import id.imron.careervault.domain.UserAccount;
import id.imron.careervault.repository.UserAccountRepository;

/** Model attributes that every page uses. */
@ControllerAdvice
public class GlobalViewAdvice {

    private final UserAccountRepository users;

    public GlobalViewAdvice(UserAccountRepository users) {
        this.users = users;
    }

    /** Full name of the signed-in user, the username if there is no account row, or null when signed out. */
    @ModelAttribute("currentUserFullName")
    String currentUserFullName(Principal principal) {
        if (principal == null) {
            return null;
        }
        return users.findByUsername(principal.getName())
                .map(UserAccount::getFullName)
                .orElse(principal.getName());
    }
}
