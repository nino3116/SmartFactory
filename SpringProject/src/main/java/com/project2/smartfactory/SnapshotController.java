package com.project2.smartfactory;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/api/snapshots")
public class SnapshotController {

    // application.properties 또는 application.yml에 설정된 스냅샷 저장 디렉토리 경로
    // 예: snapshot.save.dir=./snapshots 또는 snapshot.save.dir=C:/path/to/snapshots
    @Value("${snapshot.save.dir:./snapshots}") // 기본값으로 ./snapshots 사용
    private String snapshotSaveDir;

    /**
     * 지정된 파일 이름에 해당하는 스냅샷 이미지를 제공합니다.
     * @param filename 요청된 이미지 파일 이름 (예: defect_snapshot_YYYYMMDD_HHMMSS.png)
     * @return 이미지 파일 또는 찾을 수 없을 경우 404 응답
     */
    @GetMapping("/{filename}")
    @ResponseBody // HTTP 응답 본문에 데이터를 직접 쓰도록 지정
    public ResponseEntity<Resource> serveSnapshot(@PathVariable String filename) {
        try {
            // 스냅샷 저장 디렉토리 경로와 파일 이름을 결합하여 파일 경로 생성
            Path filePath = Paths.get(snapshotSaveDir).resolve(filename).normalize();

            // 파일이 실제로 존재하는지 확인하고 접근 가능한지 검증
            Resource resource = new UrlResource(filePath.toUri());

            // 파일이 존재하고 읽을 수 있는지 확인
            if (resource.exists() && resource.isReadable()) {
                // 파일 확장자를 기반으로 Content-Type 결정 (간단한 예시)
                String contentType = "application/octet-stream"; // 기본값
                if (filename.toLowerCase().endsWith(".png")) {
                    contentType = MediaType.IMAGE_PNG_VALUE; // image/png
                } else if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
                    contentType = MediaType.IMAGE_JPEG_VALUE; // image/jpeg
                }

                // HTTP 응답 헤더 설정 및 리소스 반환
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"") // 다운로드 대신 브라우저에 표시하려면 attachment 제거
                        .body(resource);
            } else {
                // 파일을 찾을 수 없거나 읽을 수 없을 경우 404 Not Found 응답 반환
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            // URL 형식이 잘못된 경우 (경로 문제 등)
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            // 기타 오류 발생 시
            e.printStackTrace(); // 오류 로깅
            return ResponseEntity.internalServerError().build(); // 500 Internal Server Error
        }
    }

}
