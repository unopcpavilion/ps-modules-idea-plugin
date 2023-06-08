package org.jetbrains.plugins.template.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.event.ActionEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class MyToolWindow(project: Project) {
    val content: JPanel = JPanel()
    private val runModuleButton: JButton = JButton("Run Module")
    private val loadFunctionsButton: JButton = JButton("Load Functions")
    private val modulesList: JList<String> = JList<String>()
    private val functionsTree: JTree = JTree()

    init {

        // List your PowerShell modules here, for example:
        val listModel = DefaultListModel<String>()
        val projectBaseDir = project.baseDir
        findPowerShellModules(projectBaseDir, listModel)
        modulesList.model = listModel

        content.add(JLabel("Modules:"))
        content.add(JScrollPane(modulesList))
        content.add(loadFunctionsButton)
        content.add(runModuleButton)
        content.add(JLabel("Functions:"))
        content.add(JScrollPane(functionsTree))

        loadFunctionsButton.addActionListener { _: ActionEvent ->
            val selectedModule = modulesList.selectedValue
            if (selectedModule != null) {
                // Load functions from the selected PowerShell module
                loadFunctions(selectedModule)
            }
        }

        runModuleButton.addActionListener { _: ActionEvent ->
            val selectedModule = modulesList.selectedValue
            if (selectedModule != null) {
                // Run the selected PowerShell module
                println("Running module: $selectedModule")
            }
        }
    }

    private fun findPowerShellModules(dir: VirtualFile, listModel: DefaultListModel<String>) {
        dir.children.forEach { file ->
            if (file.isDirectory) {
                findPowerShellModules(file, listModel)
            } else if (file.extension == "ps1" || file.extension == "psm1") {
                listModel.addElement(file.path)
            }
        }
    }

    private fun loadFunctions(modulePath: String) {
        val command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -Command \"& { Get-Command -Module (Import-Module '${modulePath.replace("'", "''")}' -PassThru) | Select-Object Name, @{Name='Parameters';Expression={($\\_.Parameters.Keys -join ',')}} }\""

        val process = Runtime.getRuntime().exec(command)
        val reader = BufferedReader(InputStreamReader(process.inputStream))

        val rootNode = DefaultMutableTreeNode("Functions")
        val treeModel = DefaultTreeModel(rootNode)

        reader.useLines { lines ->
            lines.drop(3).forEach { line ->
                val parts = line.trim().split(" ", limit = 2)
                if (parts.size == 2) {
                    val functionName = parts[0]
                    val parameters = parts[1].split(",").filter { it.isNotBlank() }.map { it.trim() }
                    val functionNode = DefaultMutableTreeNode(functionName)
                    rootNode.add(functionNode)

                    parameters.forEach { parameter ->
                        val parameterNode = DefaultMutableTreeNode(parameter)
                        functionNode.add(parameterNode)
                    }
                }
            }
        }

        functionsTree.model = treeModel

        process.waitFor()
    }

}
