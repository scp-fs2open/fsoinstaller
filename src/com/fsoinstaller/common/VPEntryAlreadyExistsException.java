
import java.io.IOException;

/**
 *
 * @author Ryan
 */
public class VPEntryAlreadyExistsException extends IOException {

    /**
     * Creates a new instance of <code>VPEntryAlreadyExistsException</code>
     * without detail message.
     */
    public VPEntryAlreadyExistsException() {
    }

    /**
     * Constructs an instance of <code>VPEntryAlreadyExistsException</code> with
     * the specified detail message.
     *
     * @param msg the detail message.
     */
    public VPEntryAlreadyExistsException(String msg) {
        super(msg);
    }
}
