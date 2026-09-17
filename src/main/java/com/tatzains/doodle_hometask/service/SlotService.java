package com.tatzains.doodle_hometask.service;

import com.tatzains.doodle_hometask.domain.SlotStatus;
import com.tatzains.doodle_hometask.domain.TimeSlot;
import com.tatzains.doodle_hometask.domain.User;
import com.tatzains.doodle_hometask.dto.request.CreateSlotRequest;
import com.tatzains.doodle_hometask.dto.request.UpdateSlotRequest;
import com.tatzains.doodle_hometask.exception.SlotNotFoundException;
import com.tatzains.doodle_hometask.exception.SlotNotModifiableException;
import com.tatzains.doodle_hometask.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final TimeSlotRepository timeSlotRepository;

    @Transactional
    public TimeSlot createSlot(User owner, CreateSlotRequest request) {
        TimeSlot slot = TimeSlot.builder()
                .owner(owner)
                .start(request.start())
                .end(request.end())
                .status(SlotStatus.FREE)
                .build();

        return timeSlotRepository.save(slot);
    }

    @Transactional(readOnly = true)
    public Page<TimeSlot> listOwnSlots(User owner, Pageable pageable) {
        return timeSlotRepository.findByOwnerId(owner.getId(), pageable);
    }

    @Transactional
    public TimeSlot updateSlot(User owner, UUID slotId, UpdateSlotRequest request) {
        TimeSlot slot = getOwnedFreeSlot(owner, slotId);
        slot.setStart(request.start());
        slot.setEnd(request.end());
        return timeSlotRepository.save(slot);
    }

    @Transactional
    public void deleteSlot(User owner, UUID slotId) {
        TimeSlot slot = getOwnedFreeSlot(owner, slotId);
        timeSlotRepository.delete(slot);
    }

    private TimeSlot getOwnedFreeSlot(User owner, UUID slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
                .filter(s -> s.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (slot.getStatus() != SlotStatus.FREE) {
            throw new SlotNotModifiableException(slotId);
        }

        return slot;
    }
}
