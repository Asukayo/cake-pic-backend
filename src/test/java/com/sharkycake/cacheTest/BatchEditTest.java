package com.sharkycake.cacheTest;

import com.sharkycake.picture.entity.Picture;
import com.sharkycake.picture.service.PictureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
public class BatchEditTest {

    @Resource
    private PictureService pictureService;
    private List<Picture> testPictureList;

    /**
     * 准备测试数据，从数据库查出一批数据做测试
     */
    @BeforeEach
    void setUp() {
        // 取前500条数据做测试，根据实际数据量调整
        testPictureList = pictureService.list().stream()
                .limit(2000)
                .collect(Collectors.toList());
        System.out.println("测试数据量: " + testPictureList.size() + " 条");
    }

    /**
     * 模拟编辑操作（给每张图片的name加个后缀，测完再还原）
     */
    private List<Picture> simulateEdit(List<Picture> pictures) {
        List<Picture> edited = new ArrayList<>(pictures.size());
        for (Picture pic : pictures) {
            Picture copy = new Picture();
            copy.setId(pic.getId());
            // 模拟修改分类字段，你可以换成实际的批量编辑字段
            copy.setCategory("test_batch_edit");
            edited.add(copy);
        }
        return edited;
    }
    /**
     * 工具方法：将列表按指定大小分片
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
    /**
     * 方式1，逐条更新，基线对照
     */
    @Test
    void testOneByOne(){
        List<Picture> edited = simulateEdit(testPictureList);
        long start = System.nanoTime();
        for (Picture pic : edited) {
            pictureService.updateById(pic);
        }
        long end = System.nanoTime();
        System.out.println("【逐条更新】耗时: " + (end - start) / 1_000_000.0 + " ms");
    }

    /**
     * 方式2：仅使用MyBatis-Plus 进行批处理
     */
    @Test
    void testBatchOnly(){
        List<Picture> edited = simulateEdit(testPictureList);
        long start = System.nanoTime();
        // batchSize = 100每100条提交一次
        pictureService.updateBatchById(edited,100);
        long end = System.nanoTime();
        System.out.println("【Batch批处理】耗时: " + (end - start) / 1_000_000.0 + " ms");
    }

    /**
     * 方式3：线程池+批处理
     */
    @Test
    void testThreadPoolWithBatch(){
        List<Picture> edited = simulateEdit(testPictureList);
        // 手动创建线程池
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4,
                8,
                60, TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        // 将数据按照每组10条拆分
        int batchSize = 100;
        List<List<Picture>> partition = partition(edited, batchSize);
        long start = System.nanoTime();
        // 将每个分片提交到线程池，各自执行批量更新
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<Picture> pic : partition) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                pictureService.updateBatchById(pic,pic.size()),
                executor
            );
            futures.add(future);
        }
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long end = System.nanoTime();
        System.out.println("【线程池+Batch】耗时: " + (end - start) / 1_000_000.0 + " ms");
        executor.shutdown();
    }


    @Test
    void insertTestData() {
        List<Picture> pictures = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            Picture pic = new Picture();
            pic.setName("test_pic_" + i);
            pic.setCategory("test");
            pic.setUrl("1");
            pic.setThumbnailUrl("1");
            pic.setUserId(1L);
            // 补充其他非空字段...
            pictures.add(pic);

        }
        pictureService.saveBatch(pictures, 500);
        System.out.println("插入测试数据: " + pictures.size() + " 条");
    }

}
