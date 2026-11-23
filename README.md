# 게이머즈 | 온라인 게임 거래소

경북대학교 COMP322 데이타베이스 강의 프로젝트 Team 1의 Phase 4 프로젝트입니다. Phase 2에서 설계한 Oracle DB를 사용해 Phase 3의 JDBC 콘솔 기능을 웹에서 제공하는 것이 목표입니다.

## 개발 환경

- JDK 17
- Eclipse IDE for Enterprise Java and Web Developers
- Apache Tomcat 10.1

Eclipse에서 Dynamic Web Project를 만들고 Tomcat 10.1을 실행 환경으로 지정합니다.
* * 백엔드는 Java Servlet과 Oracle Thin JDBC, 프론트엔드는 React로 구현하며, JSON API로 연동합니다.
  * React 개발 서버에서는 API 요청을 Tomcat으로 프록시하고, 로그인 상태는 Tomcat 세션 쿠키로 유지합니다.
  * 팀원별로 프로젝트 스키마와 데이터가 설치된 Oracle 계정에 연결합니다.
  * DB 접속 정보와 관리자로 사용할 기존 회원의 LoginID는 로컬 설정 파일에서 지정하며, 해당 파일은 Git에 포함하지 않습니다.

## Phase 4 범위

- Phase 3의 Q01~Q10 조회, 로그인·로그아웃, 관심등록 추가·삭제, 회원정보 조회·변경을 웹에 구현하고 Phase 2 DB에 연결합니다.
- 기존 매물을 최저가·등록 시각 순으로 자동 매칭해 구매하는 기능을 구현합니다.
- 구매에 따른 재화·아이템·주문·체결 기록을 하나의 트랜잭션으로 처리하고, 동시 요청의 데이터 일관성과 오류 시 롤백을 검증합니다.
- 각 팀원은 본인 브랜치에 실제 작업을 커밋하고 PR로 변경을 공유합니다. 팀의 작업은 GitHub 커밋 로그로 평가됩니다.

## 제출 예정 파일

| 파일 | 내용 |
| --- | --- |
| `Team1-Phase4.zip` | 압축을 풀어 실행에 필요한 파일을 갖춘 전체 소스 |
| `Team1-README.txt` 또는 PDF | 제작 환경, 실행 및 사용 방법, 기능, 유의 사항, YouTube 데모 링크 |
| `Team1-task1.txt` | GitHub 저장소 링크와 Contributors Graph |
| `Team1-Additional_task1.txt` | 여러 사용자가 동시에 접근할 때의 트랜잭션 처리 방식 |

## AI 사용 기록

Phase 4 명세 6쪽은 팀원이 작성한 코드의 AI 검증을 허용하며, 처음부터 코드 생성을 요청하는 것은 금지합니다. 제출 시 실제로 사용한 도구와 활용 방법을 상세히 적습니다.

- 초기 Github 저장소 설정에 Codex를 사용했습니다. Git 초기화와 원격 연결, README와 Git 설정 파일 작성에 활용했습니다.
- PR 상호 코드 검증과 코드의 사이드이펙트 감지용 등의 검사에 AI를 사용하였습니다.
