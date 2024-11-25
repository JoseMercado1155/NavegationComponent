package com.example.navigationcomponentexample

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.example.navigationcomponentexample.Crud.AdaptadorListener
import com.example.navigationcomponentexample.Crud.AdaptadorUsuarios
import com.example.navigationcomponentexample.Crud.DBPrueba
import com.example.navigationcomponentexample.Crud.Usuario
import com.example.navigationcomponentexample.databinding.FragmentCrudBinding
import kotlinx.coroutines.launch

class CrudFragment : Fragment(), AdaptadorListener {

    private var _binding: FragmentCrudBinding? = null
    private val binding get() = _binding!!

    var listaUsuarios: MutableList<Usuario> = mutableListOf()
    lateinit var adapter: AdaptadorUsuarios
    lateinit var room: DBPrueba
    lateinit var usuario: Usuario

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCrudBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvUsuarios.layoutManager = LinearLayoutManager(requireContext())

        room = Room.databaseBuilder(requireContext(), DBPrueba::class.java, "dbPruebas").build()

        obtenerUsuarios(room)

        binding.btnAddUpdate.setOnClickListener {
            if(binding.etUsuario.text.isNullOrEmpty() || binding.etPais.text.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "DEBES LLENAR TODOS LOS CAMPOS", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binding.btnAddUpdate.text.equals("Agregar")) {
                usuario = Usuario(
                    binding.etUsuario.text.toString().trim(),
                    binding.etPais.text.toString().trim()
                )

                agregarUsuario(room, usuario)
            } else if(binding.btnAddUpdate.text.equals("actualizar")) {
                usuario.pais = binding.etPais.text.toString().trim()

                actualizarUsuario(room, usuario)
            }
        }

    }

    fun obtenerUsuarios(room: DBPrueba){
        lifecycleScope.launch {
            listaUsuarios = room.daoUsuario().obtenerUsuarios()
            adapter = AdaptadorUsuarios(listaUsuarios, this@CrudFragment)
            binding.rvUsuarios.adapter = adapter
        }
    }

    fun agregarUsuario(room: DBPrueba, usuario: Usuario) {
        lifecycleScope.launch {
            room.daoUsuario().agregarUsuario(usuario)
            obtenerUsuarios(room)
            limpiarCampos()
        }
    }

    fun actualizarUsuario(room: DBPrueba, usuario: Usuario) {
        lifecycleScope.launch {
            room.daoUsuario().actualizarUsuario(usuario.usuario, usuario.pais)
            obtenerUsuarios(room)
            limpiarCampos()
        }
    }

    fun limpiarCampos(){
        usuario.usuario = ""
        usuario.pais = ""
        binding.etUsuario.setText("")
        binding.etPais.setText("")

        if (binding.btnAddUpdate.text.equals("actualizar")){
            binding.btnAddUpdate.setText("Agregar")
            binding.etUsuario.isEnabled = true
        }
    }

    override fun onEditItemClick(usuario: Usuario) {
        binding.btnAddUpdate.setText("actualizar")
        binding.etUsuario.isEnabled = false
        this.usuario = usuario
        binding.etUsuario.setText(this.usuario.usuario)
        binding.etPais.setText(this.usuario.pais)
    }

    override fun onDeleteItemClick(usuario: Usuario) {
        lifecycleScope.launch {
            room.daoUsuario().borrarUsuario(usuario.usuario)
            adapter.notifyDataSetChanged()
            obtenerUsuarios(room)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
