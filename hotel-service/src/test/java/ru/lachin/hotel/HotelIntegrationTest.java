package ru.lachin.hotel;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import ru.lachin.common.*;
import ru.lachin.hotel.api.HotelDtos.*;
import ru.lachin.hotel.domain.*;
import ru.lachin.hotel.repository.*;
import ru.lachin.hotel.service.*;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "security.jwt.public-key=classpath:test-public.pem"
    })
@AutoConfigureMockMvc
class HotelIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ReservationService reservations;
  @Autowired CatalogService catalog;
  @Autowired HotelRepository hotels;
  @Autowired RoomRepository rooms;
  @Autowired ReservationRepository slots;
  long hotel, room, second;
  final LocalDate start = LocalDate.now().plusDays(10), end = start.plusDays(3);

  @BeforeEach
  void setUp() {
    slots.deleteAll();
    rooms.deleteAll();
    hotels.deleteAll();
    var h = hotels.save(new Hotel("Тест", "Казань"));
    hotel = h.getId();
    room = rooms.save(new Room(h, "1", true)).getId();
    second = rooms.save(new Room(h, "2", true)).getId();
  }

  Slot slot() {
    return new Slot(UUID.randomUUID(), start, end);
  }

  @Test
  void retryIsIdempotentAndAdjacentDatesAreFree() {
    var s = slot();
    reservations.confirm(room, s);
    reservations.confirm(room, s);
    assertThat(rooms.findById(room).orElseThrow().getTimesBooked()).isEqualTo(1);
    assertThatThrownBy(() -> reservations.confirm(room, slot())).isInstanceOf(ApiException.class);
    reservations.confirm(room, new Slot(UUID.randomUUID(), end, end.plusDays(1)));
    assertThat(slots.count()).isEqualTo(2);
  }

  @Test
  void releaseBeforeConfirmBlocksLateDelivery() {
    var s = slot();
    reservations.release(room, s);
    reservations.release(room, s);
    assertThatThrownBy(() -> reservations.confirm(room, s)).isInstanceOf(ApiException.class);
    assertThat(slots.findById(s.bookingId()).orElseThrow().isReleased()).isTrue();
    assertThat(rooms.findById(room).orElseThrow().getTimesBooked()).isZero();
  }

  @Test
  void releaseFreesDatesAndDoesNotChangeOperationalAvailability() {
    var s = slot();
    reservations.confirm(room, s);
    reservations.release(room, s);
    assertThat(catalog.available(hotel, start, end, Pages.of(0, 10), true).getTotalElements())
        .isEqualTo(2);
    assertThat(rooms.findById(room).orElseThrow().isAvailable()).isTrue();
  }

  @Test
  void competingRequestsReserveOnlyOneSlot() throws Exception {
    try (var pool = Executors.newFixedThreadPool(8)) {
      var gate = new CountDownLatch(1);
      var tasks = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 8; i++)
        tasks.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    reservations.confirm(room, slot());
                    return true;
                  } catch (ApiException e) {
                    return false;
                  }
                }));
      gate.countDown();
      int accepted = 0;
      for (var f : tasks) if (f.get(10, TimeUnit.SECONDS)) accepted++;
      assertThat(accepted).isEqualTo(1);
      assertThat(slots.count()).isEqualTo(1);
    }
  }

  @Test
  void concurrentDeliveryOfSameIdIsCountedOnce() throws Exception {
    var s = slot();
    try (var pool = Executors.newFixedThreadPool(4)) {
      var tasks = new ArrayList<Future<?>>();
      for (int i = 0; i < 4; i++) tasks.add(pool.submit(() -> reservations.confirm(room, s)));
      for (var f : tasks) f.get(10, TimeUnit.SECONDS);
    }
    assertThat(rooms.findById(room).orElseThrow().getTimesBooked()).isEqualTo(1);
  }

  @Test
  void recommendationsUseCounterThenIdAndStatsClipDates() {
    var s = slot();
    reservations.confirm(room, s);
    reservations.release(room, s);
    assertThat(catalog.available(hotel, start, end, Pages.of(0, 10), true).getContent())
        .extracting(RoomView::id)
        .containsExactly(second, room);
    reservations.confirm(room, slot());
    var stats = catalog.stats(start.plusDays(1), end.plusDays(1), Pages.of(0, 10)).getContent();
    assertThat(stats.getFirst().occupiedNights()).isEqualTo(2);
    assertThat(stats.getFirst().occupancyPercent()).isEqualTo(66.67);
  }

  @Test
  void unavailableRoomCannotBeReserved() {
    catalog.updateRoom(room, new RoomUpdate("1", false));
    assertThatThrownBy(() -> reservations.confirm(room, slot())).isInstanceOf(ApiException.class);
  }

  @Test
  void apiEnforcesRolesValidationAndJwt() throws Exception {
    mvc.perform(get("/api/hotels"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.traceId").isNotEmpty());
    mvc.perform(get("/api/hotels").header("Authorization", "Bearer nonsense"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/hotels")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType("application/json")
                .content("{\"name\":\"A\",\"address\":\"B\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/hotels")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json")
                .content("{\"name\":\"\",\"address\":\"B\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/rooms")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .param("startDate", end.toString())
                .param("endDate", start.toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void adminCanCreateReadUpdateDeleteHotelAndRoom() throws Exception {
    var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    var result =
        mvc.perform(
                post("/api/hotels")
                    .with(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"Новый\",\"address\":\"Адрес\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    long h = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    mvc.perform(
            put("/api/hotels/" + h)
                .with(admin)
                .contentType("application/json")
                .content("{\"name\":\"Обновлён\",\"address\":\"Адрес\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/hotels/" + h).with(admin))
        .andExpect(jsonPath("$.name").value("Обновлён"));
    result =
        mvc.perform(
                post("/api/rooms")
                    .with(admin)
                    .contentType("application/json")
                    .content("{\"hotelId\":" + h + ",\"number\":\"1\",\"available\":true}"))
            .andExpect(status().isCreated())
            .andReturn();
    long r = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    mvc.perform(
            put("/api/rooms/" + r)
                .with(admin)
                .contentType("application/json")
                .content("{\"number\":\"2\",\"available\":false}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/rooms/" + r).with(admin)).andExpect(jsonPath("$.number").value("2"));
    mvc.perform(delete("/api/rooms/" + r).with(admin)).andExpect(status().isNoContent());
    mvc.perform(delete("/api/hotels/" + h).with(admin)).andExpect(status().isNoContent());
  }

  @Test
  void internalEndpointsRejectUserTokens() throws Exception {
    mvc.perform(
            post("/api/rooms/" + room + "/release")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType("application/json")
                .content(
                    "{\"bookingId\":\""
                        + UUID.randomUUID()
                        + "\",\"startDate\":\""
                        + start
                        + "\",\"endDate\":\""
                        + end
                        + "\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void reusedSlotIdWithChangedDatesIsRejected() {
    var s = slot();
    reservations.confirm(room, s);
    assertThatThrownBy(
            () -> reservations.release(room, new Slot(s.bookingId(), start, end.plusDays(1))))
        .isInstanceOf(ApiException.class);
    assertThat(slots.findById(s.bookingId()).orElseThrow().isReleased()).isFalse();
  }
}
