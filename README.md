# 게이머즈 | 온라인 게임 거래소

경북대학교 COMP322 Team 1의 Phase 4 프로젝트입니다. Phase 2에서 설계한 Oracle DB를 사용해 Phase 3의 JDBC 콘솔 기능을 웹에서 제공하는 것이 목표입니다.

## 개발 환경

강의 실습과 같은 JDK 17, Eclipse IDE for Enterprise Java and Web Developers, Apache Tomcat 10.1(강의의 예시는 10.1.61), JSP, Oracle Thin JDBC(`ojdbc10.jar`)를 사용합니다. Eclipse에서 Dynamic Web Project를 만들고 Tomcat 10.1을 실행 환경으로 지정합니다.

팀원별로 Phase 2 스키마가 설치된 Oracle 계정으로 연결합니다. 접속 정보는 로컬에서 설정합니다. DB 문자 집합이 `KO16MSWIN949`라면 사용 중인 Oracle JDBC 릴리스에 맞는 `orai18n.jar`도 Tomcat의 실행 클래스 경로에 추가합니다.

## Phase 4 범위

- Phase 3의 조회·삽입·삭제·변경에 해당하는 기능을 웹에 구현하고 Phase 2 DB에 연결합니다.
- 여러 사용자가 동시에 접속할 때 데이터 일관성을 지키도록 트랜잭션을 적용하고 확인합니다.
- 각 팀원은 실제 작업을 본인 브랜치에 커밋하고 PR로 변경을 공유합니다. 팀의 작업은 GitHub 커밋 로그로 평가됩니다.

## 제출

2026년 12월 1일 01:59:59까지 팀장이 LMS에 제출하며, 팀 대표 1인이 발표합니다.

| 파일 | 내용 |
| --- | --- |
| `Team1-Phase4.zip` | 압축을 풀어 실행에 필요한 파일을 갖춘 전체 소스 |
| `Team1-README.txt` 또는 PDF | 제작 환경, 실행 및 사용 방법, 기능, 유의 사항, YouTube 데모 링크 |
| `Team1-task1.txt` | GitHub 저장소 링크와 Contributors Graph |
| `Team1-Additional_task1.txt` | 여러 사용자가 동시에 접근할 때의 트랜잭션 처리 방식 |

## AI 사용 기록

Phase 4 명세 6쪽은 팀원이 작성한 코드의 AI 검증을 허용하며, 처음부터 코드 생성을 요청하는 것은 금지합니다. 제출 시 실제로 사용한 도구와 활용 방법을 상세히 적습니다.

- 초기 저장소 설정에 Codex를 사용했습니다. Phase 4 명세와 강의자료에서 요구사항과 개발 환경을 확인하고, Git 초기화와 원격 연결, README와 Git 설정 파일 작성에 활용했습니다. 이 단계에서 작성된 웹 애플리케이션 코드는 없습니다.
