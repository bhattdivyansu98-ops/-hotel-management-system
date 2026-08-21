package com.divyanshu.hotel.dao.jdbc;

import com.divyanshu.hotel.dao.PaymentDao;
import com.divyanshu.hotel.domain.Payment;
import com.divyanshu.hotel.domain.PaymentStatus;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class JdbcPaymentDao extends JdbcSupport implements PaymentDao {

    private static final String COLUMNS = "id, reservation_id, amount, currency, status, provider,"
            + " provider_reference, failure_reason, created_at";

    public JdbcPaymentDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Payment insert(Payment payment) {
        long id = insertReturningKey(
                "INSERT INTO payments (reservation_id, amount, currency, status, provider, provider_reference,"
                        + " failure_reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
                payment.getReservationId(), payment.getAmount(), payment.getCurrency(), payment.getStatus().name(),
                payment.getProvider(), payment.getProviderReference(), payment.getFailureReason());
        payment.setId(id);
        return payment;
    }

    @Override
    public Optional<Payment> findById(long id) {
        return queryOne("SELECT " + COLUMNS + " FROM payments WHERE id = ?", JdbcPaymentDao::mapPayment, id);
    }

    @Override
    public List<Payment> findByReservation(long reservationId) {
        return query("SELECT " + COLUMNS + " FROM payments WHERE reservation_id = ? ORDER BY id",
                JdbcPaymentDao::mapPayment, reservationId);
    }

    @Override
    public boolean update(Payment payment) {
        return update("UPDATE payments SET status = ?, provider_reference = ?, failure_reason = ? WHERE id = ?",
                payment.getStatus().name(), payment.getProviderReference(), payment.getFailureReason(),
                payment.getId()) == 1;
    }

    private static Payment mapPayment(ResultSet rs) throws SQLException {
        Payment payment = new Payment();
        payment.setId(rs.getLong("id"));
        payment.setReservationId(rs.getLong("reservation_id"));
        payment.setAmount(rs.getBigDecimal("amount"));
        payment.setCurrency(rs.getString("currency"));
        payment.setStatus(PaymentStatus.valueOf(rs.getString("status")));
        payment.setProvider(rs.getString("provider"));
        payment.setProviderReference(rs.getString("provider_reference"));
        payment.setFailureReason(rs.getString("failure_reason"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        payment.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        return payment;
    }
}
