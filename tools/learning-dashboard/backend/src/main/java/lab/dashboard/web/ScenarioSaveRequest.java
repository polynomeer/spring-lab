package lab.dashboard.web;

import java.util.List;

/**
 * {@code POST /api/scenarios}/{@code PUT /api/scenarios/{id}} 요청 본문 - 1단계(기존 모듈
 * 기반) 시나리오만 다룬다. {@code interpreterKind}는 요청에 없다: 사용자가 새로 등록하는
 * 시나리오는 항상 {@code NONE}이고(원본 이벤트 로그만으로 시작), 손으로 짠 해석기를 붙이는
 * 건 이 REST API가 아니라 코드 변경으로 하는 일이라는 걸 요청 모양 자체로 못 박아 둔다.
 */
public record ScenarioSaveRequest(
        String name,
        String title,
        String description,
        List<String> gradleModulePaths,
        String mainClass,
        String breakpointSpec) {
}
