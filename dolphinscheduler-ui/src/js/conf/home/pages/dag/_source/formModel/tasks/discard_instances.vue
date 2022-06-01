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
  <div class="discard_instances-model">
    <!--    丢弃旧的实例  Discard old instances -->
    <m-list-box>
      <div slot="text">{{$t('Discard old instances')}}</div>
      <div slot="content">
        <m-discard-instances
          ref="refDiscardParams"
          @on-discard-params="_onDiscardParams"
          :udp-list="discardParams"
          :hide="true">
        </m-discard-instances>
      </div>
    </m-list-box>
  </div>
</template>
<script>
  import _ from 'lodash'
  import disabledState from '@/module/mixin/disabledState'
  import mListBox from './_source/listBox'
  import mDiscardInstances from './_source/discardInstances'
  import { mapState } from 'vuex'
  import { findComponentDownward } from '@/module/util/'

  export default {
    name: 'discard_instances',
    mixins: [disabledState],
    inject: ['dagChart'],
    props: {
      backfillItem: Object
    },
    data () {
      return {
        discardParams: [],
        scriptBoxDialog: false
      }
    },
    computed: {
      ...mapState('dag', ['tasks'])
    },
    methods: {
      /**
       * verification
       */
      verification () {
        // discardParams Subcomponent verification
        if (!this.$refs.refDiscardParams._verifValue()) {
          return false
        }
        // storage
        this.$emit('on-discard-params', {
          discardParams: this.discardParams
        })
        return true
      },
      _onDiscardParams (a) {
        this.discardParams = a
      },
      getDagCanvasRef () {
        if (this.canvasRef) {
          return this.canvasRef
        } else {
          const canvas = findComponentDownward(this.dagChart, 'dag-canvas')
          this.canvasRef = canvas
          return canvas
        }
      },
      setPreNodes () {
        const canvas = this.getDagCanvasRef()
        canvas.setPreNodes(this.code, this.discardInstances, true)
      }
    },
    created () {
      let o = this.backfillItem
      // Non-null objects represent backfill
      if (!_.isEmpty(o)) {
        let discardParams = o.params.discardParams || []
        if (discardParams.length) {
          this.discardParams = discardParams
        }
      }
    },
    components: { mListBox, mDiscardInstances }
  }
</script>
