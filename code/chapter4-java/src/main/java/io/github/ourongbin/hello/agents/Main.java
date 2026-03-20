import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Integer> primes = findPrimesOptimized(100);
        System.out.println(primes);
    }

    /**
     * 使用欧拉筛法（线性筛）找出1到n之间所有的素数。
     *
     * <p>该算法时间复杂度为O(n)，空间复杂度为O(n)。每个合数仅被其最小质因子标记一次，
     * 相比传统埃拉托斯特尼筛法消除了冗余标记操作，在处理大范围素数查找时性能更优。
     *
     * <p><b>算法原理：</b></p>
     * <p>维护一个素数列表primes，对于从2到n的每个整数i：</p>
     * <ul>
     *   <li>如果isPrime[i]为true，则将i加入primes列表</li>
     *   <li>遍历primes列表中的每个素数p，标记i*p为合数</li>
     *   <li>当i能被p整除时（i % p == 0），立即跳出内层循环</li>
     * </ul>
     * <p>这样可以确保每个合数只被其最小质因子标记一次。</p>
     *
     * @param n 范围上限（包含），必须为非负整数
     * @return 包含所有素数的不可变列表，如果n小于2则返回空列表
     * @throws IllegalArgumentException 如果n为负数
     */
    public static List<Integer> findPrimesOptimized(int n) {
        // 参数验证
        if (n < 0) {
            throw new IllegalArgumentException("参数n必须为非负整数");
        }

        // 边界情况：n小于2时无素数
        if (n < 2) {
            return Collections.emptyList();
        }

        // 初始化素数标记数组，默认所有数为素数
        boolean[] isPrime = new boolean[n + 1];
        Arrays.fill(isPrime, true);
        isPrime[0] = false;
        isPrime[1] = false;

        // 预分配容量，减少扩容开销（素数密度约为1/ln(n)）
        List<Integer> primes = new ArrayList<>(Math.max(10, n / 10));

        // 欧拉筛法主循环
        for (int i = 2; i <= n; i++) {
            // 当前数i是素数，加入素数列表
            if (isPrime[i]) {
                primes.add(i);
            }

            // 标记i与已知素数的乘积为合数
            for (int p : primes) {
                // 使用long类型防止乘法溢出
                long product = (long) p * i;
                if (product > n) {
                    break;
                }

                // 标记乘积为合数
                isPrime[(int) product] = false;

                // 关键优化：当p是i的最小质因子时，停止标记
                // 保证每个合数只被其最小质因子标记一次
                if (i % p == 0) {
                    break;
                }
            }
        }

        // 返回不可变列表，防止调用者修改内部状态
        return Collections.unmodifiableList(primes);
    }
}