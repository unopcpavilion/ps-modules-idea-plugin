package org.jetbrains.plugins.template.toolWindow

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.intellij.ui.treeStructure.Tree
import java.awt.event.ActionEvent
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class MyToolWindow(project: Project) {
    val content: JPanel = JPanel()
    private val loadFunctionsButton: JButton = JButton("Load Functions")
    private val modulesList: JBList<String> = JBList()
    private val functionsTree: Tree = Tree()

    init {
        val listModel: MutableMap<String, String> = mutableMapOf()
        val projectBaseDir = project.baseDir!!
        findPowerShellModules(projectBaseDir, listModel)
        modulesList.model = JBList.createDefaultListModel(listModel.keys.toList())

        content.add(JLabel("Modules:"))
        content.add(JScrollPane(modulesList))
        content.add(loadFunctionsButton)
        content.add(JLabel("Functions:"))
        content.add(JScrollPane(functionsTree))

        loadFunctionsButton.addActionListener { _: ActionEvent ->
            val selectedModule = modulesList.selectedValue
            if (selectedModule != null) {
                loadFunctions(projectBaseDir, selectedModule)
            }
        }
    }

    private fun findPowerShellModules(dir: VirtualFile, listModel: MutableMap<String, String>) {
        dir.children.forEach { file ->
            if (file.isDirectory) {
                findPowerShellModules(file, listModel)
            } else if (file.extension == "ps1" || file.extension == "psm1") {
                listModel[file.name] = file.path
            }
        }
    }

    private fun loadFunctions(dir: VirtualFile, moduleName: String) {
        thisLogger().info("Loading functions from $moduleName")
        val command = """  
        ${'$'}modulePath = (Get-ChildItem -Path .\ -Recurse -Filter '${ moduleName.replace( "'", "''" ) }' | Select-Object -First 1).FullName  
        Import-Module -Name ${'$'}modulePath  
        ${'$'}moduleName = (Get-Item ${'$'}modulePath).BaseName  
        Get-Command -CommandType Function | Where-Object { ${'$'}_.Source -eq ${'$'}moduleName } | Select-Object Name, @{Name='Parameters';Expression={(${'$'}_.Parameters.Keys -join ',')}}  
        """.trimIndent()

        val processBuilder = ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command)
        processBuilder.directory(File(dir.path))
        thisLogger().info("Executing command: ${processBuilder.command().joinToString(" ")}")
        val process = processBuilder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))

        val rootNode = DefaultMutableTreeNode("Functions")
        val treeModel = DefaultTreeModel(rootNode)

        reader.useLines { lines ->
            lines.drop(3).forEach { line ->
                val parts = line.trim().split(" ", limit = 2)
                if(parts.size == 1) {
                    val functionName = parts[0]
                    val functionNode = DefaultMutableTreeNode(functionName)
                    rootNode.add(functionNode)
                }
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

        errorReader.useLines { lines ->
            thisLogger().info("Standard Error:")
            lines.forEach { line ->
                thisLogger().info(line)
            }
        }

        functionsTree.model = treeModel

        process.waitFor()
    }

}
