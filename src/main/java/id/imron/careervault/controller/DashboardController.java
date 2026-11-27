package id.imron.careervault.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import id.imron.careervault.service.DashboardService;

@Controller
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    String dashboard(Model model) {
        model.addAttribute("summary", dashboard.getSummary());
        return "dashboard/index";
    }
}
