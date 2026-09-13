package com.andesstay.ms_andesstay_reservations.repository;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {
}