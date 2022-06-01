/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
<template>
  <div class="user-def-params-model">
    <div class="select-listpp"
         v-for="(item,$index) in localParamsList"
         :key="item.id"
         @click="_getIndex($index)">
      <template v-if="hide">
        <!--   保留实例的最大个数 or 天数   -->
        <el-select
                style="width: 200px;"
                size="small"
                @change="_handleDirectChanged"
                v-model="localParamsList[$index].type"
                :disabled="isDetails">
          <el-option
                  v-for="city in discardMethodList"
                  :key="city.type"
                  :value="city.type"
                  :label="city.label">
          </el-option>
        </el-select>
        <!--   所有实例 or 保留失败   -->
        <el-select
          style="width: 130px;"
          size="small"
          @change="_handleDirectChanged"
          v-model="localParamsList[$index].feature"
          :disabled="isDetails">
          <el-option
            v-for="city in featureList"
            :key="city.feature"
            :value="city.feature"
            :label="city.label">
          </el-option>
        </el-select>
      </template>
      <el-input
              :disabled="isDetails || item.ifFixed"
              type="number"
              size="small"
              v-model.number="localParamsList[$index].value"
              :placeholder="$t('value(required)')"
              min="1"
              maxlength="5"
              @blur="_verifValue()"
              :style="inputStyle">
      </el-input>
      <span class="lt-add" v-show="!item.ifFixed">
        <a href="javascript:" style="color:#ff0000;" @click="!isDetails && _removeUdp($index)" >
          <em class="el-icon-delete" :class="_isDetails" data-toggle="tooltip" :title="$t('Delete')" ></em>
        </a>
      </span>
    </div>
    <span class="add" v-if="$index === (localParamsList.length - 1)">
        <a href="javascript:" @click="!isDetails && _addUdp()" >
          <em class="el-icon-circle-plus-outline" :class="_isDetails" data-toggle="tooltip" :title="$t('Add')"></em>
        </a>
      </span>
    <span class="add-dp" v-if="!localParamsList.length">
      <a href="javascript:" @click="!isDetails && _addUdp()" >
        <em class="iconfont el-icon-circle-plus-outline" :class="_isDetails" data-toggle="tooltip" :title="$t('Add')"></em>
      </a>
    </span>
  </div>
</template>
<script>
  import _ from 'lodash'
  import i18n from '@/module/i18n'
  import { discardMethodList, featureList } from './commcon'
  import disabledState from '@/module/mixin/disabledState'
  export default {
    name: 'user-def-params',
    data () {
      return {
        // Direct data Custom parameter type support IN
        discardMethodList: discardMethodList,
        featureList: featureList,
        // Increased data
        localParamsList: [],
        // Current execution index
        localParamsIndex: null
      }
    },
    mixins: [disabledState],
    props: {
      udpList: Array,
      // hide direct/type
      hide: {
        type: Boolean,
        default: true
      }
    },
    methods: {
      /**
       * Current index
       */
      _getIndex (index) {
        this.localParamsIndex = index
      },
      /**
       * handle direct
       */
      _handleDirectChanged () {
        this._verifValue('value')
      },
      /**
       * handle type
       */
      _handleTypeChanged () {
        this._verifValue('value')
      },
      /**
       * delete item
       */
      _removeUdp (index) {
        this.localParamsList.splice(index, 1)
        this._verifValue('value')
      },
      /**
       * add
       */
      _addUdp () {
        this.localParamsList.push({
          type: 'nums',
          feature: 'all',
          value: ''
        })
      },
      /**
       * Verify that the value exists or is empty or less than 1
       */
      _verifValue (value) {
        let arr = []
        let flag = true
        let flagLess = false
        _.map(this.localParamsList, v => {
          arr.push(v.value)
          if (v.value === '') {
            flag = false
          } else {
            let num = v.value
            if (num < 1) {
              flagLess = true
            }
          }
        })
        if (!flag) {
          this.$message.warning(`${i18n.$t('value is empty')}`)
          return false
        }
        if (flagLess) {
          this.$message.warning(`${i18n.$t('value cannot less than 1')}`)
          return false
        }
        this.$emit('on-local-params', _.cloneDeep(this.localParamsList))
        return true
      }
    },

    watch: {
      // Monitor data changes
      udpList () {
        this.localParamsList = this.udpList
      }
    },
    created () {
      this.localParamsList = this.udpList
    },
    computed: {
      inputStyle () {
        return `width:${this.hide ? 150 : 252}px`
      }
    },
    mounted () {
    },
    components: { }
  }
</script>

<style lang="scss" rel="stylesheet/scss">
  .user-def-params-model {
    .select-listpp {
      margin-bottom: 6px;
      .lt-add {
        padding-left: 4px;
        line-height: 32px;
        a {
          .iconfont, [class^="el-icon"] {
            font-size: 17px;
            vertical-align: middle;
            display: inline-block;
            margin-top: 0;
          }
        }
      }
    }
    .add {
      line-height: 32px;
      a {
        color: #000;
        .iconfont, [class^="el-icon"] {
          font-size: 18px;
          vertical-align: middle;
          display: inline-block;
          margin-top: 0;
        }
      }
    }
    .add-dp {
      a {
        color: #0097e0;
        .iconfont, [class^="el-icon"] {
          font-size: 18px;
          vertical-align: middle;
          display: inline-block;
          margin-top: 2px;
        }
      }
    }
  }
</style>
