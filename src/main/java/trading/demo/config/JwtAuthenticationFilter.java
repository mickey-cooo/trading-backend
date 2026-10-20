package trading.demo.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpMethod;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import trading.demo.security.JwtTokenProvider;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private static final RequestMatcher AUTH_ROUTES = PathPatternRequestMatcher.withDefaults()
			.matcher("/api/v1/auth/**");

	// a signed HS256 token with sub/iat/exp is ~200 chars
	private static final int MAX_AUTH_HEADER_LENGTH = 2048;

	private final HandlerExceptionResolver handlerExceptionResolver;

	private final JwtTokenProvider jwtService;
	private final UserDetailsService userDetailsService;

	public JwtAuthenticationFilter(HandlerExceptionResolver handlerExceptionResolver, JwtTokenProvider jwtService,
			UserDetailsService userDetailsService) {
		this.handlerExceptionResolver = handlerExceptionResolver;
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	private static final List<RequestMatcher> PUBLIC_MARKET_ROUTES = Arrays
			.stream(SecurityConfig.PUBLIC_MARKET_GET_PATHS)
			.<RequestMatcher>map(path -> PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, path))
			.toList();

	private static final List<RequestMatcher> PUBLIC_DOCS_ROUTES = Arrays.stream(SecurityConfig.PUBLIC_DOCS_PATHS)
			.<RequestMatcher>map(path -> PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, path))
			.toList();

	// a stale token must not block auth routes, public prices or the API docs
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return AUTH_ROUTES.matches(request) || PUBLIC_MARKET_ROUTES.stream().anyMatch(route -> route.matches(request))
				|| PUBLIC_DOCS_ROUTES.stream().anyMatch(route -> route.matches(request));
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		final String authHeader = request.getHeader("Authorization");

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		if (authHeader.length() > MAX_AUTH_HEADER_LENGTH) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		try {
			final String jwt = authHeader.substring(7);
			final String userEmail = jwtService.extractUsername(jwt);

			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

			if (userEmail != null && authentication == null) {
				var userDetails = userDetailsService.loadUserByUsername(userEmail);

				if (jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled()) {
					UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,
							null, userDetails.getAuthorities());

					authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authToken);
				}
			}
		} catch (JwtException | UsernameNotFoundException | IllegalArgumentException e) {
			// bad token only; anything else (e.g. database down) propagates as 500
			// no @ExceptionHandler matched: resolver writes nothing, so answer 401 here
			if (handlerExceptionResolver.resolveException(request, response, null, e) == null) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			}
			return;
		}
		filterChain.doFilter(request, response);
	}
}
