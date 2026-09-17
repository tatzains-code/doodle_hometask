package com.tatzains.doodle_hometask.mapper;

import com.tatzains.doodle_hometask.domain.Meeting;
import com.tatzains.doodle_hometask.domain.MeetingParticipant;
import com.tatzains.doodle_hometask.dto.response.MeetingResponse;
import com.tatzains.doodle_hometask.dto.response.ParticipantResponse;

import java.util.List;

public final class MeetingMapper {

    private MeetingMapper() {
    }

    public static MeetingResponse toResponse(Meeting meeting, List<MeetingParticipant> participants) {
        List<ParticipantResponse> participantResponses = participants.stream()
                .map(p -> new ParticipantResponse(p.getUser().getId(), p.getUser().getName(), p.getRole()))
                .toList();

        return new MeetingResponse(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getStart(),
                meeting.getEnd(),
                participantResponses
        );
    }
}
