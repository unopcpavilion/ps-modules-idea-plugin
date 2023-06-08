import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.ToolWindowManager

class MyProjectManagerListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val myToolWindow = toolWindowManager.getToolWindow("My Tool Window")
        myToolWindow?.activate(null)
    }

    // Implement other required methods (leave them empty if not needed)  
    override fun projectClosed(project: Project) {}
    override fun canCloseProject(project: Project): Boolean = true
    override fun projectClosing(project: Project) {}
}  
