#include "CudaDBSCAN.h"
#include "cuda_runtime.h"
#include "device_functions.h"
#include "cublas_v2.h"
#include "device_launch_parameters.h"
#include <iostream>
#include <fstream>
#include <sstream>
#include <cstdlib>
#include <ctime>
#include <math.h>
#include <queue>
#include <string.h>
#include <stdlib.h>
#include <vector>

using namespace std;

struct Point {
	float		dimensions[128];
	int			cluster;
	int			noise;  //-1 noise;
    string      img;
};

float __device__ dev_euclidean_distance(const Point &src, const Point &dest) {
    float res = 0.0;
    for(int i=0; i<128; i++){
        res += (src.dimensions[i] - dest.dimensions[i]) * (src.dimensions[i] - dest.dimensions[i]);
    }
	return sqrt(res);
}

/*to get the total list*/
void __global__ dev_region_query(Point* sample, int num, int* neighbors, float eps, int min_nb) {

	unsigned int	tid = blockIdx.x * blockDim.x + threadIdx.x;
	unsigned int	line,col,pointer = tid;
	unsigned int	count;

	while (pointer < num * num) {//全场唯一id
		line = pointer / num;
		col = pointer % num;
		float radius;
		if (line <= col) {
			radius = dev_euclidean_distance(sample[line], sample[col]);
			if (radius <= eps) {
				neighbors[pointer] = 1;
			}
			neighbors[col * num + line] = neighbors[pointer];//对角线
		}
		pointer += blockDim.x * gridDim.x;
	}
	__syncthreads();

	pointer = tid;
	while (pointer < num) {
		count = 1;
		line = pointer * num;
		for (int i = 0; i < num; i++) {
			if (pointer != i && neighbors[line+i]) {//包含p点邻域元素个数
				count++;
			}
		}
		if (count >= min_nb) {
			sample[pointer].noise++;
		}
		pointer += blockDim.x * gridDim.x;
	}
}

void host_algorithm_dbscan(Point* host_sample, int num, float eps, int min_nb, int block_num, int thread_num) {
	/*sample*/
	Point* cuda_sample;
	cudaMalloc((void**)&cuda_sample, num * sizeof(Point));
	cudaMemcpy(cuda_sample, host_sample, num * sizeof(Point), cudaMemcpyHostToDevice);

	/*neighbor list*/
	int *host_neighbor = new int[num*num]();
	int *dev_neighbor;
	cudaMalloc((void**)&dev_neighbor, num * num * sizeof(int));

	dev_region_query << <block_num, thread_num >> > (cuda_sample, num, dev_neighbor, eps, min_nb);

	cudaMemcpy(host_sample, cuda_sample, num * sizeof(Point), cudaMemcpyDeviceToHost);
	cudaMemcpy(host_neighbor, dev_neighbor, num * num * sizeof(int), cudaMemcpyDeviceToHost);

    cudaFree(cuda_sample);cudaFree(dev_neighbor);

	queue<int> expand;
	int cur_cluster = 0;

	for (int i = 0; i < num; i++) {
		if (host_sample[i].noise >= 0 && host_sample[i].cluster < 1) {
			host_sample[i].cluster = ++cur_cluster;
			int src = i * num;
			for (int j = 0; j < num; j++) {
				if (host_neighbor[src + j]) {
					host_sample[j].cluster = cur_cluster;
					expand.push(j);
				}
			}

			while (!expand.empty()) {/*expand the cluster*/
				if (host_sample[expand.front()].noise >= 0) {
					src = expand.front() * num;
					for (int j = 0; j < num; j++) {
						if (host_neighbor[src + j] && host_sample[j].cluster < 1) {
							host_sample[j].cluster = cur_cluster;
							expand.push(j);
						}
					}
				}
				expand.pop();
			}
		}
	}

	ofstream fout;
    fout.open("result.html");
    for (int i = 0; i < num; i++) {
        fout <<"<img src='"<< host_sample[i].img << "'/>" <<host_sample[i].cluster<< endl;
    }
    fout.close();
}

// 读取文件行数
int countLines(const char *filename){
    ifstream fin(filename, ios::in);
    int n=0;
    string lineStr;
    while(getline(fin, lineStr)) n++;
    return n;
}

extern "C"
JNIEXPORT void JNICALL Java_CudaDBSCAN_runDBSCAN__Ljava_lang_String_2IFIII
(JNIEnv *env, jobject obj, jstring jfile_name, jint jsize, jfloat jeps, jint jmin_pts, jint jblock_num, jint jthread_num){
	// step1: 读取文件
	const char *file_name;
    file_name = env->GetStringUTFChars(jfile_name, NULL);  /* 获得传入的文件名，将其转换为C-String (char*) */
    /* file_name == NULL意味着JVM为C-String (char*)分配内存失败 */
    if(file_name == NULL)  {
       cout << "------>no file\n" << endl;
    }

    // step2: 从step1中获取的文件中解析出所有的特征点，初始化结构体Point数组
    // 获取文件的行数
    //int point_count = countLines(file_name);
    int point_count = static_cast<int>(jsize);
    Point *host_sample = new Point[point_count];
    // 然后将每行的数据读到Point结构体中
    int sample_num = 0;
    string lineStr;
    ifstream fin(file_name, ios::in);
    while(getline(fin, lineStr)){
        stringstream ss(lineStr);
        vector<string> lineArray;
        string str;
        // 按照逗号分隔
        while (getline(ss, str, ','))
            lineArray.push_back(str);/* 将文件中每一行存入到vector中，其中lineArray[0]存放的是特征值 */
        // 分离出特征值即lineArray[0]后，是一个以“_”分割的字符串，解析出来存到Point结构体的dimensions中
        char *datas;
        const int len = lineArray[0].length();
        datas = new char[len + 1];
        strcpy(datas, lineArray[0].c_str());
        const char dims[2] = "_";
        char *token;
        // 获取第一个子字符串
        token = strtok(datas, dims);
        // 继续获取其他的子字符串
        int i=0;
        while( token != NULL )
        {
            host_sample[sample_num].dimensions[i++] = atof(token);
            token = strtok(NULL, dims);
        }
        host_sample[sample_num].noise = -1;
        host_sample[sample_num].cluster = -1;
        host_sample[sample_num].img = lineArray[1];
        sample_num++;
        if(sample_num == point_count){
            break;
        }
    }

	cout << "------>TOTAL SAMPLE NUMB0->" << sample_num << "<-----" << endl;

    clock_t start, finish;
    start = clock();

	host_algorithm_dbscan(host_sample, sample_num, static_cast<float>(jeps), static_cast<int>(jmin_pts), static_cast<int>(jblock_num), static_cast<int>(jthread_num));

	finish = clock();

    delete []host_sample;

	cout<< file_name << " speed time: "<< (finish-start)*1.0/CLOCKS_PER_SEC <<"s\n"<<endl;

	env->ReleaseStringUTFChars(jfile_name, file_name);  /* 通知JVM释放String所占的内存 */
}