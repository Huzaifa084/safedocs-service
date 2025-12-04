package org.devaxiom.safedocs.security;

import lombok.Getter;
import lombok.ToString;
import org.devaxiom.safedocs.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;

@Getter
public class UserDetailsImpl implements UserDetails, Principal {

    @Serial
    private static final long serialVersionUID = 1L;

    @ToString.Include
    private final Long id;

    @ToString.Include
    private final String email;

    private final String username;
    private final String password;
    private final boolean active;
    private final boolean deleted;
    private final Long tokenVersion;

    public UserDetailsImpl(
            Long id,
            String email,
            String username,
            String password,
            boolean active,
            boolean deleted,
            Long tokenVersion
    ) {
        this.id = id;
        this.email = email;
        this.username = (username != null && !username.isBlank()) ? username : email;
        this.password = password;
        this.active = active;
        this.deleted = deleted;
        this.tokenVersion = tokenVersion == null ? 0L : tokenVersion;
    }

    public static UserDetailsImpl from(User user) {
        if (user == null) return null;

        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getIsActive()),
                Boolean.TRUE.equals(user.getDeleted()),
                user.getTokenVersion()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptySet();
    }

    @Override
    public String getName() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active && !deleted;
    }
}
