package pay.conflux.backend.identity.usecase;

import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.dto.CreateAdminRequest;

public interface CreateAdminUseCase {
  AdminProfileDto execute(CreateAdminRequest request);
}
