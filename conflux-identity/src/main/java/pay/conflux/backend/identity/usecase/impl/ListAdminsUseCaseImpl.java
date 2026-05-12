package pay.conflux.backend.identity.usecase.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.dto.PaginationRequest;
import pay.conflux.backend.identity.dto.AdminProfileSummaryDto;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.mapper.AdminProfileMapper;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.ListAdminsUseCase;

@UseCase
@RequiredArgsConstructor
public class ListAdminsUseCaseImpl implements ListAdminsUseCase {

  private final AdminProfileRepository adminProfileRepository;
  private final UserRepository userRepository;
  private final AdminProfileMapper adminProfileMapper;

  @Override
  public Page<AdminProfileSummaryDto> execute(PaginationRequest pagination, AdminTier tierFilter) {
    Page<AdminProfile> page =
        tierFilter == null
            ? adminProfileRepository.findAll(pagination.toPageable())
            : adminProfileRepository.findByAdminTier(tierFilter, pagination.toPageable());

    List<UUID> userIds = page.getContent().stream().map(AdminProfile::getUserId).toList();
    Map<UUID, User> usersById = new HashMap<>();
    for (User u : userRepository.findAllById(userIds)) {
      usersById.put(u.getId(), u);
    }

    return page.map(
        profile -> adminProfileMapper.toSummaryDto(usersById.get(profile.getUserId()), profile));
  }
}
