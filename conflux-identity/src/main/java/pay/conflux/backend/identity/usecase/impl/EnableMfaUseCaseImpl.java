package pay.conflux.backend.identity.usecase.impl;

import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.util.Utils;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.MfaEnableResponse;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.EnableMfaUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class EnableMfaUseCaseImpl implements EnableMfaUseCase {

  private static final String ISSUER = "ConfluxPay";

  private final UserRepository userRepository;

  private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
  private final QrGenerator qrGenerator = new ZxingPngQrGenerator();

  @Override
  @Transactional
  public MfaEnableResponse execute(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

    if (user.isMfaEnabled()) {
      throw new InvalidOperationStateException("MFA is already enabled for this user");
    }

    String secret = secretGenerator.generate();

    user.setMfaSecret(secret);
    user.setMfaEnabled(true);

    QrData qrData =
        new QrData.Builder()
            .label(user.getIdentifier())
            .secret(secret)
            .issuer(ISSUER)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build();

    String provisioningUri = qrData.getUri();
    byte[] imageData;
    try {
      imageData = qrGenerator.generate(qrData);
    } catch (dev.samstevens.totp.exceptions.QrGenerationException e) {
      throw new IllegalStateException("Failed to generate QR code", e);
    }
    String qrCodeBase64 = Utils.getDataUriForImage(imageData, qrGenerator.getImageMimeType());

    userRepository.save(user);

    return new MfaEnableResponse(secret, provisioningUri, qrCodeBase64);
  }
}
