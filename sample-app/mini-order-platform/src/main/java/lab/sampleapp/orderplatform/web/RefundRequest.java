package lab.sampleapp.orderplatform.web;

/** memberId는 환불받을 대상 회원 - 요청을 보낸(관리자) 회원과는 다른 사람이다. */
public record RefundRequest(String memberId, long amountWon) {
}
