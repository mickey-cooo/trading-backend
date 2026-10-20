package trading.demo.model.dto.adminDto;

import jakarta.validation.constraints.NotNull;
import trading.demo.model.enums.Common;

// The new status for a user or a market. Only ACTIVE and SUSPENDED are accepted.
public record ChangeStatusRequest(@NotNull Common status) {
}
