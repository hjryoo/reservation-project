package kr.hhplus.be.server;

import kr.hhplus.be.server.application.service.ConcertService;
import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.ConcertDate;
import kr.hhplus.be.server.domain.model.ConcertStatus;
import kr.hhplus.be.server.domain.repository.ConcertDateRepository;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private ConcertDateRepository concertDateRepository;

    @InjectMocks
    private ConcertService concertService;

    private Concert testConcert;
    private ConcertDate testConcertDate;

    @BeforeEach
    void setUp() {
        // 현재 구현된 Concert 도메인 모델에 맞춰 수정
        testConcert = Concert.create(
                "테스트 콘서트",         // title
                "테스트 아티스트",       // artist
                "서울 올림픽 경기장",     // venue
                50,                   // totalSeats
                50000L                // price (Long 타입)
        );

        // ConcertDate 테스트 객체 생성
        LocalDateTime concertDateTime = LocalDateTime.now().plusDays(7);
        testConcertDate = ConcertDate.create(
                1L,                   // concertId
                concertDateTime,      // concertDateTime
                concertDateTime.minusHours(1),  // startTime
                concertDateTime.plusHours(3),   // endTime
                50                   // totalSeats
        );
    }

    @Test
    @DisplayName("예약 가능한 콘서트 목록을 조회할 수 있다")
    void getAvailableConcerts() {
        // Given
        List<Concert> mockConcerts = Arrays.asList(testConcert);
        when(concertRepository.findAvailableConcerts(eq(ConcertStatus.AVAILABLE), any(LocalDateTime.class)))
                .thenReturn(mockConcerts);

        // When
        List<Concert> result = concertService.getAvailableConcerts();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("테스트 콘서트");
        assertThat(result.get(0).getArtist()).isEqualTo("테스트 아티스트");
        assertThat(result.get(0).getVenue()).isEqualTo("서울 올림픽 경기장");
        verify(concertRepository).findAvailableConcerts(eq(ConcertStatus.AVAILABLE), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("콘서트 ID로 상세 정보를 조회할 수 있다")
    void getConcertById() {
        // Given
        Long concertId = 1L;
        when(concertRepository.findById(concertId)).thenReturn(Optional.of(testConcert));

        // When
        Concert result = concertService.getConcertById(concertId);

        // Then
        assertThat(result.getTitle()).isEqualTo("테스트 콘서트");
        assertThat(result.getArtist()).isEqualTo("테스트 아티스트");
        assertThat(result.getVenue()).isEqualTo("서울 올림픽 경기장");
        assertThat(result.getTotalSeats()).isEqualTo(50);
        assertThat(result.getPrice()).isEqualTo(50000L);
        verify(concertRepository).findById(concertId);
    }

    @Test
    @DisplayName("존재하지 않는 콘서트 ID로 조회시 예외가 발생한다")
    void getConcertById_NotFound() {
        // Given
        Long concertId = 999L;
        when(concertRepository.findById(concertId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> concertService.getConcertById(concertId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("콘서트를 찾을 수 없습니다: " + concertId);

        verify(concertRepository).findById(concertId);
    }

    @Test
    @DisplayName("날짜 범위로 콘서트를 조회할 수 있다")
    void getConcertsByDateRange() {
        // Given
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        List<Concert> mockConcerts = Arrays.asList(testConcert);

        when(concertRepository.findConcertsByDateRange(startDate, endDate))
                .thenReturn(mockConcerts);

        // When
        List<Concert> result = concertService.getConcertsByDateRange(startDate, endDate);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("테스트 콘서트");
        verify(concertRepository).findConcertsByDateRange(startDate, endDate);
    }

    @Test
    @DisplayName("시작 날짜가 종료 날짜보다 늦으면 예외가 발생한다")
    void getConcertsByDateRange_InvalidDateRange() {
        // Given
        LocalDateTime startDate = LocalDateTime.now().plusDays(30);
        LocalDateTime endDate = LocalDateTime.now();

        // When & Then
        assertThatThrownBy(() -> concertService.getConcertsByDateRange(startDate, endDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시작 날짜는 종료 날짜보다 이전이어야 합니다.");
    }

    @Test
    @DisplayName("아티스트명으로 콘서트를 조회할 수 있다")
    void getConcertsByArtist() {
        // Given
        String artist = "테스트 아티스트";
        List<Concert> mockConcerts = Arrays.asList(testConcert);

        when(concertRepository.findByArtistAndFutureDates(eq(artist), any(LocalDateTime.class)))
                .thenReturn(mockConcerts);

        // When
        List<Concert> result = concertService.getConcertsByArtist(artist);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getArtist()).isEqualTo(artist);
        verify(concertRepository).findByArtistAndFutureDates(eq(artist), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("아티스트명이 비어있으면 예외가 발생한다")
    void getConcertsByArtist_EmptyArtist() {
        // When & Then
        assertThatThrownBy(() -> concertService.getConcertsByArtist(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("아티스트명은 필수입니다.");

        assertThatThrownBy(() -> concertService.getConcertsByArtist(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("아티스트명은 필수입니다.");
    }

    @Test
    @DisplayName("장소명으로 콘서트를 조회할 수 있다")
    void getConcertsByVenue() {
        // Given
        String venue = "서울 올림픽 경기장";
        List<Concert> mockConcerts = Arrays.asList(testConcert);

        when(concertRepository.findByVenue(venue)).thenReturn(mockConcerts);

        // When
        List<Concert> result = concertService.getConcertsByVenue(venue);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVenue()).isEqualTo(venue);
        verify(concertRepository).findByVenue(venue);
    }

    @Test
    @DisplayName("장소명이 비어있으면 예외가 발생한다")
    void getConcertsByVenue_EmptyVenue() {
        // When & Then
        assertThatThrownBy(() -> concertService.getConcertsByVenue(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("장소명은 필수입니다.");

        assertThatThrownBy(() -> concertService.getConcertsByVenue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("장소명은 필수입니다.");
    }

    // ===== ConcertDate 관련 테스트 =====

    @Test
    @DisplayName("예약 가능한 날짜 목록을 조회할 수 있다")
    void getAvailableDates() {
        // Given
        int limit = 10;
        List<ConcertDate> mockDates = Arrays.asList(testConcertDate);
        when(concertDateRepository.findAllAvailableDates(limit)).thenReturn(mockDates);

        // When
        List<ConcertDate> result = concertService.getAvailableDates(limit);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConcertId()).isEqualTo(1L);
        verify(concertDateRepository).findAllAvailableDates(limit);
    }

    @Test
    @DisplayName("특정 월의 예약 가능한 날짜를 조회할 수 있다")
    void getAvailableDatesByMonth() {
        // Given
        LocalDate month = LocalDate.now();
        int limit = 10;
        List<ConcertDate> mockDates = Arrays.asList(testConcertDate);
        when(concertDateRepository.findAvailableDatesByMonth(month, limit)).thenReturn(mockDates);

        // When
        List<ConcertDate> result = concertService.getAvailableDatesByMonth(month, limit);

        // Then
        assertThat(result).hasSize(1);
        verify(concertDateRepository).findAvailableDatesByMonth(month, limit);
    }

    @Test
    @DisplayName("월 정보가 null이면 예외가 발생한다")
    void getAvailableDatesByMonth_NullMonth() {
        // When & Then
        assertThatThrownBy(() -> concertService.getAvailableDatesByMonth(null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회할 월은 필수입니다.");
    }

    @Test
    @DisplayName("특정 콘서트의 일정을 조회할 수 있다")
    void getConcertDatesByConcertId() {
        // Given
        Long concertId = 1L;
        List<ConcertDate> mockDates = Arrays.asList(testConcertDate);
        when(concertDateRepository.findByConcertId(concertId)).thenReturn(mockDates);

        // When
        List<ConcertDate> result = concertService.getConcertDatesByConcertId(concertId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConcertId()).isEqualTo(concertId);
        verify(concertDateRepository).findByConcertId(concertId);
    }

    @Test
    @DisplayName("특정 콘서트의 특정 일시 일정을 조회할 수 있다")
    void getConcertDate() {
        // Given
        Long concertId = 1L;
        LocalDateTime dateTime = LocalDateTime.now().plusDays(7);
        when(concertDateRepository.findByConcertIdAndDateTime(concertId, dateTime))
                .thenReturn(Optional.of(testConcertDate));

        // When
        ConcertDate result = concertService.getConcertDate(concertId, dateTime);

        // Then
        assertThat(result.getConcertId()).isEqualTo(concertId);
        verify(concertDateRepository).findByConcertIdAndDateTime(concertId, dateTime);
    }

    @Test
    @DisplayName("존재하지 않는 공연 일정 조회시 예외가 발생한다")
    void getConcertDate_NotFound() {
        // Given
        Long concertId = 1L;
        LocalDateTime dateTime = LocalDateTime.now().plusDays(7);
        when(concertDateRepository.findByConcertIdAndDateTime(concertId, dateTime))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> concertService.getConcertDate(concertId, dateTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 공연 일정을 찾을 수 없습니다.");

        verify(concertDateRepository).findByConcertIdAndDateTime(concertId, dateTime);
    }

    @Test
    @DisplayName("날짜 범위로 공연 일정을 조회할 수 있다")
    void getConcertDatesByRange() {
        // Given
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        List<ConcertDate> mockDates = Arrays.asList(testConcertDate);
        when(concertDateRepository.findByDateRange(startDate, endDate)).thenReturn(mockDates);

        // When
        List<ConcertDate> result = concertService.getConcertDatesByRange(startDate, endDate);

        // Then
        assertThat(result).hasSize(1);
        verify(concertDateRepository).findByDateRange(startDate, endDate);
    }

    @Test
    @DisplayName("다가오는 공연 일정을 조회할 수 있다")
    void getUpcomingDates() {
        // Given
        LocalDateTime fromDate = LocalDateTime.now();
        int limit = 5;
        List<ConcertDate> mockDates = Arrays.asList(testConcertDate);
        when(concertDateRepository.findUpcomingDates(fromDate, limit)).thenReturn(mockDates);

        // When
        List<ConcertDate> result = concertService.getUpcomingDates(fromDate, limit);

        // Then
        assertThat(result).hasSize(1);
        verify(concertDateRepository).findUpcomingDates(fromDate, limit);
    }

    @Test
    @DisplayName("모든 콘서트 목록을 조회할 수 있다")
    void getAllConcerts() {
        // Given
        List<Concert> mockConcerts = Arrays.asList(testConcert);
        when(concertRepository.findAll()).thenReturn(mockConcerts);

        // When
        List<Concert> result = concertService.getAllConcerts();

        // Then
        assertThat(result).hasSize(1);
        verify(concertRepository).findAll();
    }

    @Test
    @DisplayName("모든 공연 일정을 조회할 수 있다")
    void getAllConcertDates() {
        // Given
        int limit = 100;
        List<ConcertDate> mockDates = Arrays.asList(testConcertDate);
        when(concertDateRepository.findUpcomingDates(any(LocalDateTime.class), eq(limit)))
                .thenReturn(mockDates);

        // When
        List<ConcertDate> result = concertService.getAllConcertDates(limit);

        // Then
        assertThat(result).hasSize(1);
        verify(concertDateRepository).findUpcomingDates(any(LocalDateTime.class), eq(limit));
    }
}