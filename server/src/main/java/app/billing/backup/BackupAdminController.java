package app.billing.backup;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/backups")
public class BackupAdminController {

    private final BackupService backups;

    public BackupAdminController(BackupService backups) {
        this.backups = backups;
    }

    @GetMapping
    public BackupService.BackupList list() {
        return backups.list();
    }

    @PostMapping
    public BackupService.BackupFile create() {
        return backups.createBackup();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BackupService.BackupFile upload(@RequestParam("file") MultipartFile file) {
        return backups.upload(file);
    }

    @PostMapping("/{name}/restore")
    public void restore(@PathVariable String name) {
        backups.scheduleRestore(name);
    }

    @DeleteMapping("/restore-pending")
    public void cancelRestore() {
        backups.cancelPendingRestore();
    }

    @PostMapping("/open-folder")
    public void openFolder() {
        backups.openBackupFolder();
    }
}
