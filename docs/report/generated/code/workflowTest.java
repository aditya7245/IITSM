// com/iitsm/workflow/DuplicatesAndEscalationWorkflowTest.java
void anUnacknowledgedP1BecomesAtRiskThenViolatedThenEscalatesThroughAllThreeLevels() throws Exception {
    String created = reportPaymentOutage("emp1", "Payment gateway timeout");
    long id = number(created, "$.incident.id");
    assertEquals("P1", text(created, "$.incident.priority"));
    assertEquals(0, level(id));

    clock.advance(Duration.ofMinutes(4));                                  // 80% of the 5 minute response window
    monitor.runOnce();
    escalation.runOnce();
    assertEquals("AT_RISK", text(incident(id), "$.slaStatus"));
    assertEquals(0, level(id));

    clock.advance(Duration.ofMinutes(1));                                  // exactly the deadline: not violated yet
    monitor.runOnce();
    escalation.runOnce();
    assertEquals("AT_RISK", text(incident(id), "$.slaStatus"));
    assertEquals(1, level(id));                                            // level 1: the team lead

    clock.advance(Duration.ofSeconds(1));                                  // one second late
    monitor.runOnce();
