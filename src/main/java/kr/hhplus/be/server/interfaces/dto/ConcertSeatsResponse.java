package kr.hhplus.be.server.interfaces.dto;

import kr.hhplus.be.server.application.GetConcertSeatsService.ConcertInfo;
import kr.hhplus.be.server.application.GetConcertSeatsService.SeatInfo;
import kr.hhplus.be.server.application.GetConcertSeatsService.SeatSummary;

import java.util.List;

public class ConcertSeatsResponse {
    private final ConcertInfo concertInfo;
    private final List<SeatInfo> seats;
    private final SeatSummary summary;

    public ConcertSeatsResponse(ConcertInfo concertInfo,
                                List<SeatInfo> seats,
                                SeatSummary summary) {
        this.concertInfo = concertInfo;
        this.seats = seats;
        this.summary = summary;
    }

    public ConcertInfo getConcertInfo() { return concertInfo; }
    public List<SeatInfo> getSeats() { return seats; }
    public SeatSummary getSummary() { return summary; }
}
