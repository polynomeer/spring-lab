package lab.experiments.mvcerror;

import jakarta.validation.constraints.NotBlank;

public record CreateWidgetRequest(@NotBlank String name) {
}
