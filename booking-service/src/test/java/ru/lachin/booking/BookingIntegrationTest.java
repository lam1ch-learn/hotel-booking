package ru.lachin.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.lachin.booking.api.BookingDtos.*;
import ru.lachin.booking.client.HotelClient;
import ru.lachin.booking.domain.*;
import ru.lachin.booking.repository.*;
import ru.lachin.booking.service.*;
import ru.lachin.common.*;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "security.jwt.public-key=classpath:test-public.pem",
      "security.jwt.private-key=classpath:test-private.pem",
      "booking.recovery-delay=3600000"
    })
@AutoConfigureMockMvc
class BookingIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired BookingService service;
  @Autowired BookingStore store;
  @Autowired BookingRepository bookings;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired JwtEncoder encoder;
  @MockitoBean HotelClient hotel;
  long owner, other;
  final LocalDate start = LocalDate.now().plusDays(10), end = start.plusDays(2);

  @BeforeEach
  void setUp() {
    reset(hotel);
    bookings.deleteAll();
    users.deleteAll();
    owner =
        users.save(new User("maxim", passwords.encode("test-password"), User.Role.USER)).getId();
    other =
        users.save(new User("other", passwords.encode("test-password"), User.Role.USER)).getId();
  }

  Create request() {
    return new Create(UUID.randomUUID(), 1L, null, false, start, end);
  }

  @Test
  void pendingIsCommittedBeforeRemoteCallAndRetryReturnsSameBooking() {
    var in = request();
    doAnswer(
            call -> {
              assertThat(
                      bookings
                          .findByUserIdAndRequestId(owner, in.requestId())
                          .orElseThrow()
                          .getStatus())
                  .isEqualTo(Booking.Status.PENDING);
              return null;
            })
        .when(hotel)
        .confirm(anyLong(), any());
    var b = service.create(owner, in);
    assertThat(b.status()).isEqualTo(Booking.Status.CONFIRMED);
    assertThat(service.create(owner, in).id()).isEqualTo(b.id());
    verify(hotel, times(1)).confirm(anyLong(), any());
    assertThat(bookings.count()).isEqualTo(1);
  }

  @Test
  void reusedRequestWithChangedPayloadIsConflict() {
    var in = request();
    service.create(owner, in);
    assertThatThrownBy(
            () -> service.create(owner, new Create(in.requestId(), 2L, null, false, start, end)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void remoteFailureCancelsAndReleasesSlot() {
    doThrow(new IllegalStateException("503")).when(hotel).confirm(anyLong(), any());
    assertThatThrownBy(() -> service.create(owner, request())).isInstanceOf(ApiException.class);
    var b = bookings.findAll().getFirst();
    assertThat(b.getStatus()).isEqualTo(Booking.Status.CANCELLED);
    assertThat(b.isReleasePending()).isFalse();
    verify(hotel).release(eq(1L), any());
  }

  @Test
  void unavailableRoomReturns409() {
    doThrow(ApiException.conflict("busy")).when(hotel).confirm(anyLong(), any());
    assertThatThrownBy(() -> service.create(owner, request()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void failedCompensationIsRetriedByRecovery() {
    doThrow(new IllegalStateException("timeout")).when(hotel).confirm(anyLong(), any());
    doThrow(new IllegalStateException("offline")).when(hotel).release(anyLong(), any());
    assertThatThrownBy(() -> service.create(owner, request())).isInstanceOf(ApiException.class);
    var b = bookings.findAll().getFirst();
    assertThat(b.isReleasePending()).isTrue();
    doNothing().when(hotel).release(anyLong(), any());
    new BookingRecovery(store, service, Duration.ofSeconds(30), 50).recover();
    assertThat(store.get(b.getId()).releasePending()).isFalse();
    verify(hotel, times(2)).release(anyLong(), any());
  }

  @Test
  void interruptedPendingBookingIsCancelledByRecovery() {
    var p = store.prepare(owner, request());
    store.assign(p.booking().id(), 1);
    new BookingRecovery(store, service, Duration.ZERO, 50).recover();
    assertThat(store.get(p.booking().id()).status()).isEqualTo(Booking.Status.CANCELLED);
    verify(hotel).release(anyLong(), any());
  }

  @Test
  void cancelIsIdempotentAndOnlyOwnerCanRead() {
    var b = service.create(owner, request());
    assertThatThrownBy(() -> store.owned(b.id(), other)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.cancel(b.id(), other)).isInstanceOf(ApiException.class);
    service.cancel(b.id(), owner);
    service.cancel(b.id(), owner);
    verify(hotel, times(1)).release(anyLong(), any());
    assertThat(store.history(other, Pages.of(0, 10))).isEmpty();
  }

  @Test
  void autoSelectionIgnoresRoomId() {
    when(hotel.recommend(null, start, end))
        .thenReturn(List.of(new HotelClient.Room(7, 1, "7", true, 0)));
    var b = service.create(owner, new Create(UUID.randomUUID(), 999L, null, true, start, end));
    assertThat(b.roomId()).isEqualTo(7);
    verify(hotel).confirm(eq(7L), any());
  }

  @Test
  void noRoomsCancelsWithoutUnnecessaryRelease() {
    when(hotel.recommend(null, start, end)).thenReturn(List.of());
    assertThatThrownBy(
            () ->
                service.create(owner, new Create(UUID.randomUUID(), null, null, true, start, end)))
        .isInstanceOf(ApiException.class);
    assertThat(bookings.findAll().getFirst().getStatus()).isEqualTo(Booking.Status.CANCELLED);
    verify(hotel, never()).release(anyLong(), any());
  }

  @Test
  void duplicateParallelRequestsHaveOneRemoteEffect() throws Exception {
    var in = request();
    try (var pool = Executors.newFixedThreadPool(6)) {
      var tasks = new ArrayList<Future<View>>();
      for (int i = 0; i < 6; i++) tasks.add(pool.submit(() -> service.create(owner, in)));
      var ids = new HashSet<UUID>();
      for (var f : tasks) ids.add(f.get(10, TimeUnit.SECONDS).id());
      assertThat(ids).hasSize(1);
    }
    assertThat(bookings.count()).isEqualTo(1);
    verify(hotel, times(1)).confirm(anyLong(), any());
  }

  @Test
  void cancellationWinsAgainstLateConfirmation() throws Exception {
    var entered = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    doAnswer(
            c -> {
              entered.countDown();
              assertThat(resume.await(5, TimeUnit.SECONDS)).isTrue();
              return null;
            })
        .when(hotel)
        .confirm(anyLong(), any());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var future = pool.submit(() -> service.create(owner, request()));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      var id = bookings.findAll().getFirst().getId();
      service.cancel(id, owner);
      resume.countDown();
      assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class);
      assertThat(store.get(id).status()).isEqualTo(Booking.Status.CANCELLED);
    }
  }

  @Test
  void authRegistrationAndRealJwtWork() throws Exception {
    var result =
        mvc.perform(
                post("/api/users/register")
                    .contentType("application/json")
                    .content("{\"username\":\"new-user\",\"password\":\"valid-password\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andReturn();
    var tree =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result.getResponse().getContentAsString());
    String token = tree.get("accessToken").asText();
    mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    assertThat(users.findByUsername("new-user").orElseThrow().getPassword())
        .isNotEqualTo("valid-password");
    mvc.perform(
            post("/api/users/auth")
                .contentType("application/json")
                .content("{\"username\":\"new-user\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/users/auth")
                .contentType("application/json")
                .content("{\"username\":\"new-user\",\"password\":\"valid-password\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void invalidAndExpiredJwtReturn401() throws Exception {
    mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/bookings").header("Authorization", "Bearer bad"))
        .andExpect(status().isUnauthorized());
    var claims =
        JwtClaimsSet.builder()
            .issuer("hotel-booking")
            .audience(List.of("hotel-api"))
            .subject("1")
            .issuedAt(Instant.now().minusSeconds(7200))
            .expiresAt(Instant.now().minusSeconds(3600))
            .claim("roles", List.of("USER"))
            .build();
    var token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void bookingEndpointsValidateDatesAndHideOtherOwners() throws Exception {
    var auth =
        jwt().jwt(j -> j.subject("" + owner)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    mvc.perform(
            post("/api/bookings")
                .with(auth)
                .contentType("application/json")
                .content(
                    "{\"requestId\":\""
                        + UUID.randomUUID()
                        + "\",\"roomId\":1,\"startDate\":\""
                        + end
                        + "\",\"endDate\":\""
                        + start
                        + "\"}"))
        .andExpect(status().isBadRequest());
    var b = service.create(other, request());
    mvc.perform(get("/api/bookings/" + b.id()).with(auth)).andExpect(status().isNotFound());
    mvc.perform(delete("/api/bookings/" + b.id()).with(auth)).andExpect(status().isNotFound());
    mvc.perform(get("/api/bookings").with(auth).param("size", "1000"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void userCrudIsAdminOnly() throws Exception {
    var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    var user = jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    mvc.perform(get("/api/users").with(user)).andExpect(status().isForbidden());
    var r =
        mvc.perform(
                post("/api/users")
                    .with(admin)
                    .contentType("application/json")
                    .content(
                        "{\"username\":\"created\",\"password\":\"valid-password\",\"role\":\"USER\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    long id =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(r.getResponse().getContentAsString())
            .get("id")
            .asLong();
    mvc.perform(
            patch("/api/users/" + id)
                .with(admin)
                .contentType("application/json")
                .content("{\"username\":\"renamed\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/users/" + id).with(admin))
        .andExpect(jsonPath("$.username").value("renamed"))
        .andExpect(jsonPath("$.password").doesNotExist());
    mvc.perform(delete("/api/users/" + id).with(admin)).andExpect(status().isNoContent());
  }

  @Test
  void unavailableRecommendationsReturn503() throws Exception {
    when(hotel.recommend(null, start, end))
        .thenThrow(new org.springframework.web.client.ResourceAccessException("offline"));
    mvc.perform(
            get("/api/bookings/recommendations")
                .with(
                    jwt()
                        .jwt(j -> j.subject("" + owner))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }
}
