package id.imron.careervault.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The sign-in page; Spring Security handles the form submission and sign-out. */
@Controller
public class AuthController {

    @GetMapping("/login")
    String login() {
        return "auth/login";
    }
}
