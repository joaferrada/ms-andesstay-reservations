package com.andesstay.ms_andesstay_reservations.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Compatibilidad (EP3): principal del token de quien creo la reserva. Ya no se usa para mostrar ni filtrar.
    private String clienteUsername;

    // Huesped de la reserva (obligatorios; se validan al crear en HuespedResolver).
    // Columnas nullable para que ddl-auto=update pueda agregarlas a una tabla con filas existentes.
    @Column(length = 150)
    private String huespedNombre;

    @Column(length = 254)
    private String huespedEmail;

    // Principal del token de quien creo la reserva (nunca viene del body)
    @Column(length = 254)
    private String creadoPor;

    // Id de la unidad en ms-andesstay-catalog (opcional). Si existe, confirmar descuenta un cupo
    private Long unidadId;

    private String alojamientoNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Double precioTotal;

    // EP3: estado del ciclo de vida y auditoria basica
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus estado;

    private LocalDateTime creadaEn;
    private LocalDateTime actualizadaEn;

    // Constructores, Getters y Setters
    public ReservationEntity() {}

    @PrePersist
    void alCrear() {
        if (estado == null) {
            estado = ReservationStatus.CREADA;
        }
        creadaEn = LocalDateTime.now();
        actualizadaEn = creadaEn;
    }

    @PreUpdate
    void alActualizar() {
        actualizadaEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClienteUsername() { return clienteUsername; }
    public void setClienteUsername(String clienteUsername) { this.clienteUsername = clienteUsername; }

    public String getHuespedNombre() { return huespedNombre; }
    public void setHuespedNombre(String huespedNombre) { this.huespedNombre = huespedNombre; }

    public String getHuespedEmail() { return huespedEmail; }
    public void setHuespedEmail(String huespedEmail) { this.huespedEmail = huespedEmail; }

    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }

    public Long getUnidadId() { return unidadId; }
    public void setUnidadId(Long unidadId) { this.unidadId = unidadId; }

    public String getAlojamientoNombre() { return alojamientoNombre; }
    public void setAlojamientoNombre(String alojamientoNombre) { this.alojamientoNombre = alojamientoNombre; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public Double getPrecioTotal() { return precioTotal; }
    public void setPrecioTotal(Double precioTotal) { this.precioTotal = precioTotal; }

    public ReservationStatus getEstado() { return estado; }
    public void setEstado(ReservationStatus estado) { this.estado = estado; }

    public LocalDateTime getCreadaEn() { return creadaEn; }
    public void setCreadaEn(LocalDateTime creadaEn) { this.creadaEn = creadaEn; }

    public LocalDateTime getActualizadaEn() { return actualizadaEn; }
    public void setActualizadaEn(LocalDateTime actualizadaEn) { this.actualizadaEn = actualizadaEn; }
}
