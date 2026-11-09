package filesharex.file.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class FileStorageFactory
{

    private final Map<String, FileStorageStrategy> strategies;

    public FileStorageFactory(List<FileStorageStrategy> strategyList)
    {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(FileStorageStrategy::getType, Function.identity()));
        log.info("Initialized file storage strategies: {}", strategies.keySet());
    }

    public FileStorageStrategy getStrategy(String type)
    {
        FileStorageStrategy strategy = strategies.get(type);
        if (strategy == null)
        {
            throw new IllegalArgumentException("Unknown file storage type: " + type);
        }
        return strategy;
    }
}
