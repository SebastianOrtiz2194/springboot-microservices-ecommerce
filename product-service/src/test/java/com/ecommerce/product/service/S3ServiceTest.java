package com.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/** Unit tests for {@link S3Service}. */
@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock private S3Template s3Template;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Template, BUCKET, Duration.ofHours(1), 5);
    }

    @Test
    void uploadImage_uploadsAndReturnsKey() throws IOException {
        MockMultipartFile file =
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", "image-bytes".getBytes());

        String key = s3Service.uploadImage(file);

        assertThat(key).matches("^[0-9a-f-]{36}-photo\\.jpg$");
        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        ArgumentCaptor<ObjectMetadata> metaCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Template)
                .upload(eq(BUCKET), eq(key), streamCaptor.capture(), metaCaptor.capture());
        assertThat(metaCaptor.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(metaCaptor.getValue().getContentLength())
                .isEqualTo("image-bytes".getBytes().length);
    }

    @Test
    void uploadImage_sanitizesSpecialCharactersInFilename() throws IOException {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "photo name:with,chars.jpg", "image/jpeg", new byte[] {1});

        String key = s3Service.uploadImage(file);

        assertThat(key).endsWith("photo_name_with_chars.jpg");
        verify(s3Template)
                .upload(eq(BUCKET), eq(key), any(InputStream.class), any(ObjectMetadata.class));
    }

    @Test
    void uploadImage_rejectsEmptyFile() {
        MockMultipartFile file =
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> s3Service.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        verifyNoInteractions(s3Template);
    }

    @Test
    void uploadImage_rejectsUnsupportedContentType() {
        MockMultipartFile file =
                new MockMultipartFile("file", "script.html", "text/html", "<script/>".getBytes());

        assertThatThrownBy(() -> s3Service.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported content type");
        verifyNoInteractions(s3Template);
    }

    @Test
    void uploadImage_rejectsPathTraversalFilename() {
        MockMultipartFile file =
                new MockMultipartFile("file", "../../etc/passwd", "image/jpeg", new byte[] {1});

        assertThatThrownBy(() -> s3Service.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file name");
        verifyNoInteractions(s3Template);
    }

    @Test
    void uploadImage_rejectsOversizedFile() {
        s3Service = new S3Service(s3Template, BUCKET, Duration.ofHours(1), 0);
        MockMultipartFile file =
                new MockMultipartFile("file", "big.jpg", "image/jpeg", new byte[] {1});

        assertThatThrownBy(() -> s3Service.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File too large");
        verifyNoInteractions(s3Template);
    }

    @Test
    void createPresignedUrl_returnsNullForNullOrBlankKey() {
        assertThat(s3Service.createPresignedUrl(null)).isNull();
        assertThat(s3Service.createPresignedUrl("  ")).isNull();
        verifyNoInteractions(s3Template);
    }

    @Test
    void createPresignedUrl_passesThroughLegacyUrl() {
        String legacy = "https://legacy.example/image.jpg";

        assertThat(s3Service.createPresignedUrl(legacy)).isEqualTo(legacy);
        verifyNoInteractions(s3Template);
    }

    @Test
    void createPresignedUrl_generatesSignedUrlForKey() throws Exception {
        when(s3Template.createSignedGetURL(anyString(), anyString(), any(Duration.class)))
                .thenReturn(new URL("https://s3.amazonaws.com/" + BUCKET + "/s3-key-1"));

        String result = s3Service.createPresignedUrl("s3-key-1");

        assertThat(result).isEqualTo("https://s3.amazonaws.com/test-bucket/s3-key-1");
        verify(s3Template).createSignedGetURL(BUCKET, "s3-key-1", Duration.ofHours(1));
    }
}
