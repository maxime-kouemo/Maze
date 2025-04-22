package maze.labyrinth.table

/**
 * Project: Labyrinth
 * Package: maze.labyrinth.table
 * Created by mamboa on 2016-05-11.
 * Updated by mamboa on 2025-04-20
 * Description:
 */
// Class DisjointSet
// Implementation de la structure des ensembles disjoints avec compression des chemins et union par rang
// Implementation originale: Thierry Lavoie avril 2012
class DisjointSet(size: Int) {
    fun union(el1: Int, el2: Int) {
        val root1 = find(el1)
        val root2 = find(el2)
        if (root1 == root2) {
            return
        }

        if (s[root2] < s[root1]) {
            s[root1] = root2
        } else {
            if (s[root1] == s[root2]) {
                --s[root1]
            }
            s[root2] = root1
        }
    }

    fun find(x: Int): Int {
        if (s[x] < 0) {
            return x
        } else {
            s[x] = find(s[x])
            return s[x]
        }
    }

    private val s = IntArray(size)

    init {
        for (i in 0 until size) {
            s[i] = -1
        }
    }
}


