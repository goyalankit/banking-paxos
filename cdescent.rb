require 'Matrix'
A = Matrix[[0, 0, 1, 0], [0, 0, 0, 2], [0, 3, 0, 0]]
B = Matrix.column_vector([1, 2, 3])
e = Matrix.column_vector([0, 0, 0, 0])
X = Matrix.column_vector([0, 0, 0, 0])
#X = Matrix.column_vector([0, 0, 0, ])

H =  A.transpose * A

#bt = B.transpose
#ai = A.column(i)

#Xhat = X[i] = 0
#

100.times do |p|
    4.times do |i|
        ei = e
        ei(i) = 1
        X(i) = ((B.transpose * A.column(i)) - (X.transpose * H * ei))/(2 * 1 + H(i)(i))
    end
     p X
end
