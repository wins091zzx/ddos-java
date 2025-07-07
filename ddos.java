import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoadGenerator {

    private static final String USER_AGENT = "Mozilla/5.0 (Android; Linux armv7l; rv:10.0.1) Gecko/20100101 Firefox/10.0.1 Fennec/10.0.1";
    private static final Duration TIMEOUT = Duration.ofSeconds(10); // Thời gian chờ kết nối

    private final HttpClient httpClient;
    private final String targetUrl;

    public LoadGenerator(String targetUrl) {
        this.targetUrl = targetUrl;
        // Xây dựng một phiên bản HttpClient dùng chung để đạt hiệu quả
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // Hoặc HTTP_2 nếu ưu tiên và được hỗ trợ
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);

        System.out.print("Nhập URL: ");
        String url = in.nextLine();

        if (url.isEmpty()) {
            System.err.println("URL không được để trống. Đang thoát.");
            in.close();
            return;
        }

        System.out.print("Nhập số lượng yêu cầu đồng thời (luồng) [mặc định: 2000]: ");
        String amountInput = in.nextLine();
        int concurrentRequests = 2000; // Giá trị mặc định
        if (!amountInput.isEmpty()) {
            try {
                concurrentRequests = Integer.parseInt(amountInput);
                if (concurrentRequests <= 0) {
                    System.err.println("Số lượng yêu cầu đồng thời phải là số dương. Đang sử dụng mặc định (2000).");
                    concurrentRequests = 2000;
                }
            } catch (NumberFormatException e) {
                System.err.println("Định dạng số không hợp lệ cho luồng. Đang sử dụng mặc định (2000).");
            }
        }

        System.out.print("Nhập phương thức (GET/POST) [mặc định: POST]: ");
        String method = in.nextLine().toUpperCase();
        if (!method.equals("GET") && !method.equals("POST")) {
            System.err.println("Phương thức không hợp lệ. Đang sử dụng mặc định (POST).");
            method = "POST";
        }

        System.out.println("\nĐang bắt đầu tạo tải cho URL: " + url);
        System.out.println("Số lượng yêu cầu đồng thời: " + concurrentRequests);
        System.out.println("Phương thức: " + method);

        LoadGenerator generator = new LoadGenerator(url);

        // Kiểm tra kết nối đơn giản
        try {
            System.out.println("Đang thực hiện kiểm tra kết nối ban đầu...");
            HttpResponse<String> response = generator.httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("User-Agent", USER_AGENT)
                            .GET()
                            .timeout(TIMEOUT)
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            System.out.println("Trạng thái kết nối ban đầu: " + response.statusCode());
        } catch (IOException | InterruptedException e) {
            System.err.println("Không thể kết nối đến URL: " + e.getMessage());
            in.close();
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        System.out.println("Đang bắt đầu tạo tải...");
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestSeq = i; // Để sử dụng trong lambda
            final String finalMethod = method; // Để sử dụng trong lambda
            executor.submit(() -> {
                try {
                    if (finalMethod.equals("POST")) {
                        generator.sendPostRequest(requestSeq);
                    } else {
                        generator.sendGetRequest(requestSeq);
                    }
                } catch (IOException | InterruptedException e) {
                    System.err.println("Yêu cầu " + requestSeq + " thất bại: " + e.getMessage());
                }
            });
        }

        executor.shutdown(); // Bắt đầu tắt một cách có trật tự
        try {
            // Chờ tất cả các tác vụ hoàn thành hoặc hết thời gian chờ sau 5 phút
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                System.out.println("Một số tác vụ không hoàn thành trong thời gian chờ. Đang buộc tắt.");
                executor.shutdownNow(); // Buộc tắt nếu không phải tất cả tác vụ đã hoàn thành
            }
        } catch (InterruptedException e) {
            System.err.println("Tạo tải bị gián đoạn trong quá trình tắt.");
            executor.shutdownNow(); // Hủy các tác vụ đang thực thi
            Thread.currentThread().interrupt(); // Khôi phục trạng thái ngắt
        }

        System.out.println("Tạo tải đã hoàn tất.");
        in.close();
    }

    private void sendPostRequest(int seq) throws IOException, InterruptedException {
        String requestBody = "load_data=" + System.currentTimeMillis(); // Nội dung động
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(TIMEOUT)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Yêu cầu POST " + seq + " hoàn thành! Trạng thái: " + response.statusCode());
    }

    private void sendGetRequest(int seq) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("User-Agent", USER_AGENT)
                .GET()
                .timeout(TIMEOUT)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Yêu cầu GET " + seq + " hoàn thành! Trạng thái: " + response.statusCode());
    }
}
