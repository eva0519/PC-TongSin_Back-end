package com.computatongsin.computatongsin.service;

import com.computatongsin.computatongsin.dto.ResponseDto;
import com.computatongsin.computatongsin.dto.TokenDto;
import com.computatongsin.computatongsin.dto.req.LoginReqDto;
import com.computatongsin.computatongsin.dto.req.SignupReqDto;
import com.computatongsin.computatongsin.dto.req.TokenRequestDto;
import com.computatongsin.computatongsin.entity.Member;
import com.computatongsin.computatongsin.repository.MemberRepository;
import com.computatongsin.computatongsin.repository.RefreshTokenRepository;
import com.computatongsin.computatongsin.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService implements UserDetailsService {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member member = memberRepository
                .findByUsername(username)
                .orElseThrow(
                        ()->new UsernameNotFoundException(username+"을 찾을 수 없습니다")
                );
        return new MemberDetails(member);
    }

    // 회원가입
    @Transactional
    public ResponseDto<?> createAccount(SignupReqDto signupReqDto) {
        String username = signupReqDto.getUsername();
        String password = signupReqDto.getPassword();
        String passwordConfirm = signupReqDto.getPasswordConfirm();
        String nickname = signupReqDto.getNickname();
        if(memberRepository.existsByUsername(username)) {
            throw new RuntimeException("이미 가입된 유저입니다");
        }
        if(!password.equals(passwordConfirm)) {
            throw new RuntimeException("비밀번호와 비밀번호 확인이 일치하지 않습니다");
        }
        Member member = new Member(username, passwordEncoder.encode(password), nickname, Authority.ROLE_USER);
        return ResponseDto.success(memberRepository.save(member));
    }

    // 로그인
    @Transactional
    public ResponseEntity<?> loginAccount(LoginReqDto loginReqDto) {

        UsernamePasswordAuthenticationToken authenticationToken = loginReqDto.toAuthentication();
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        Member member = memberRepository.findByUsername(loginReqDto.getUsername()).orElse(null);
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        RefreshToken refreshToken = RefreshToken.builder()
                .key(authentication.getName())
                .value(tokenDto.getRefreshToken())
                .build();
        refreshTokenRepository.save(refreshToken);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(JwtFilter.AUTHORIZATION_HEADER, JwtFilter.BEARER_PREFIX + tokenDto.getAccessToken());
        httpHeaders.add("Refresh-Token", tokenDto.getRefreshToken());

        return new ResponseEntity<>(ResponseDto.success(member), httpHeaders, HttpStatus.OK);
    }

    // 토큰 재발급
    @Transactional
    public TokenDto reissue(TokenRequestDto tokenRequestDto) {
        if (!tokenProvider.validateToken(tokenRequestDto.getRefreshToken())) {
            throw new RuntimeException(("Refresh Token이 유효하지 않습니다"));
        }

        Authentication authentication = tokenProvider.getAuthentication(tokenRequestDto.getAccessToken());

        RefreshToken refreshToken = refreshTokenRepository.findByKey(authentication.getName())
                .orElseThrow(()->new RuntimeException("로그아웃 된 사용자입니다"));

        if (!refreshToken.getValue().equals(tokenRequestDto.getRefreshToken())) {
            throw new RuntimeException("토큰의 유저 정보가 일치하지 않습니다");
        }

        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        RefreshToken refreshRefreshToken = refreshToken.updateValue(tokenDto.getRefreshToken());
        refreshTokenRepository.save(refreshRefreshToken);

        return tokenDto;
    }

    public ResponseDto<?> duplicateCheckId(String username) {
        if (memberRepository.existsByUsername(username))
            return ResponseDto.fail("400", "중복된 아이디 값입니다");
        else {
            return ResponseDto.success("사용할 수 있는 아이디 입니다");
        }
    }
}
