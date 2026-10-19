cmake_minimum_required(VERSION 3.22.1)
project(dshpty C)

add_library(dshpty SHARED pty.c)
find_library(log-lib log)
target_link_libraries(dshpty ${log-lib})
