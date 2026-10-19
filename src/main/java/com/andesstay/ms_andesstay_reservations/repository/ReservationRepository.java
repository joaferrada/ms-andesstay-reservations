package com.andesstay.ms_andesstay_reservations.repository;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

    List<ReservationEntity> findByEstado(ReservationStatus estado);

    List<ReservationEntity> findByClienteUsername(String clienteUsername);

    List<ReservationEntity> findByClienteUsernameAndEstado(String clienteUsername, ReservationStatus estado);
}
