package com.shadhinpay.identity.usecase;

import com.shadhinpay.identity.dto.LoginRequest;
import com.shadhinpay.identity.dto.LoginResponse;

public interface AuthenticateUserUseCase {
  LoginResponse execute(LoginRequest request);
}
