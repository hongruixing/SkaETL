<template>
  <v-card class="mb-5">
    <v-card-title>
      <div>
        <div class="headline">Select your validations</div>
        <div><span class="grey--text"><small>Optional</small></span></div>
      </div>
    </v-card-title>
    <v-card-text>
      <v-dialog v-model="dialog" max-width="500px">
        <v-btn color="success" slot="activator">add validation
          <v-icon>add</v-icon>
        </v-btn>

        <v-card>
          <v-card-title>
            <span class="headline">{{ formTitle }}</span>
          </v-card-title>
          <v-card-text>
            <v-select label="Type Validation" v-model="editedItem.typeValidation" :items="type" item-value="name">
              <template slot="selection" slot-scope="data">
                {{data.item.name}}
              </template>
              <template slot="item" slot-scope="data">
                <v-list-tile-content>
                  <v-list-tile-title v-html="data.item.name"></v-list-tile-title>
                  <v-list-tile-sub-title v-html="data.item.description"></v-list-tile-sub-title>
                </v-list-tile-content>
              </template>
            </v-select>

            <v-layout row wrap v-show="isMandatory()">
              <v-text-field label="Mandatory (separated by ;)"
                            v-model="editedItem.parameterValidation.mandatory" required></v-text-field>
            </v-layout>

            <v-flex xs12 sm6 md6 v-show="isBlackList()">
              <v-layout row wrap>
                <v-text-field label="Key" v-model="keyBlackList" required></v-text-field>
                <v-text-field label="Value" v-model="valueBlackList" required></v-text-field>
                <v-btn color="primary" v-on:click.native="addItemBlackList">add BlackList Item</v-btn>
              </v-layout>
              <v-layout>
                <v-flex v-for="itemBlack in editedItem.parameterValidation.blackList">
                  <v-btn color="purple lighten-2" smallclose @input="removeBlackList(item)">
                    {{itemBlack.key}}-{{itemBlack.value}}
                  </v-btn>
                </v-flex>
              </v-layout>
            </v-flex>

            <v-layout row wrap v-show="isMaxField()">
              <v-text-field label="Maximum field "
                            v-model="editedItem.parameterValidation.maxFields" required></v-text-field>
            </v-layout>

            <v-layout row wrap v-show="isMaxMessageSize()">
              <v-text-field label="Maximum Size message"
                            v-model="editedItem.parameterValidation.maxMessageSize" required></v-text-field>
            </v-layout>

            <v-layout row wrap v-show="isFieldExist()">
              <v-text-field label="Field exist" v-model="editedItem.parameterValidation.fieldExist"
                            required></v-text-field>
            </v-layout>

            <v-container fluid pa-0 v-show="isTimestampValidation()">
                <v-checkbox label="Validate in past"
                            persistent-hint
                            v-model="editedItem.parameterValidation.validateInThePast"></v-checkbox>
                <v-flex v-show="editedItem.parameterValidation.validateInThePast">
                  <v-text-field label="Unit in the past" v-model="editedItem.parameterValidation.unitInThePast"
                                required></v-text-field>

                  <v-select label="Chrono unit in the past"
                            v-model="editedItem.parameterValidation.chronoUnitInThePast"
                            v-bind:items="chronoUnits"/>
                </v-flex>

                <v-checkbox label="Validate events after fixed date in past"
                            persistent-hint
                            v-model="editedItem.parameterValidation.validateAfterFixedDate"></v-checkbox>

                <v-flex v-show="editedItem.parameterValidation.validateAfterFixedDate">
                  <v-menu
                    ref="datemenu"
                    :close-on-content-click="false"
                    v-model="datemenu"
                    :nudge-right="40"
                    lazy
                    transition="scale-transition"
                    offset-y
                    full-width
                    max-width="290px"
                    min-width="290px"
                  >

                    <v-text-field
                      slot="activator"
                      v-model="editedItem.parameterValidation.lowerFixedDate"
                      label="Fixed date"
                      prepend-icon="event"
                    ></v-text-field>
                    <v-date-picker v-model="editedItem.parameterValidation.lowerFixedDate" scrollable no-title
                                   @input="datemenu = false"></v-date-picker>
                  </v-menu>
                </v-flex>
                <v-checkbox label="Validate in future"
                            persistent-hint
                            v-model="editedItem.parameterValidation.validateInFuture"></v-checkbox>
                <v-flex v-show="editedItem.parameterValidation.validateInFuture">
                  <v-text-field label="Unit in future" v-model="editedItem.parameterValidation.unitInFuture"
                                required></v-text-field>
                  <v-select label="Chrono unit in future"
                            v-model="editedItem.parameterValidation.chronoUnitInFuture"
                            v-bind:items="chronoUnits"/>
                </v-flex>
              </v-container>


          </v-card-text>
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn color="blue darken-1" flat @click.native="close">Cancel</v-btn>
            <v-btn color="blue darken-1" flat @click.native="save">Save</v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>


    </v-card-text>
    <v-data-table :headers="headers" :items="processValidations" hide-actions>
      <template slot="items" slot-scope="props">
        <td>{{props.item.typeValidation}}</td>
        <td class="justify-center layout px-0">
          <v-btn icon class="mx-0" @click="editItem(props.item)">
            <v-icon color="teal">edit</v-icon>
          </v-btn>
          <v-btn icon class="mx-0" @click="deleteItem(props.item)">
            <v-icon color="pink">delete</v-icon>
          </v-btn>
        </td>
      </template>
    </v-data-table>
    <v-card-actions>
      <v-btn color="primary" style="width: 120px" @click.native="$emit('previousStep')">
        <v-icon>navigate_before</v-icon>
        Previous
      </v-btn>
      <v-btn color="primary" style="width: 120px" @click.native="$emit('nextStep')">Next
        <v-icon>navigate_next</v-icon>
      </v-btn>
    </v-card-actions>
  </v-card>
</template>


<script>
  export default {
    props: {
      processValidations: {
        type: Array,
        required: true
      }
    },
    data: function () {
      return {
        dialog: false,
        datemenu: false,
        editedItem: {
          "parameterValidation": {
            "mandatory": '',
            "blackList": [],
            "maxFields": "",
            "maxMessageSize": "",
            "fieldExist": "",
            "validateInThePast": false,
            "unitInThePast": 1,
            "chronoUnitInThePast": "DAYS",
            "validateInFuture": false,
            "unitInFuture": 1,
            "chronoUnitInFuture": "DAYS",
            "validateAfterFixedDate": false
          }
        },
        defaultItem: {
          "parameterValidation": {
            "mandatory": '',
            "blackList": [],
            "maxFields": "",
            "maxMessageSize": "",
            "fieldExist": "",
            "validateInThePast": false,
            "unitInThePast": 1,
            "chronoUnitInThePast": "DAYS",
            "validateInFuture": false,
            "unitInFuture": 1,
            "chronoUnitInFuture": "DAYS",
            "validateAfterFixedDate": false
          }
        },
        editedIndex: -1,
        headers: [
          {text: 'Type', value: 'typeParser'},
          {text: 'Actions', value: 'typeParser', sortable: false}
        ],
        keyBlackList: '',
        valueBlackList: '',
        type: [],
        chronoUnits: ["MILLIS", "SECONDS", "MINUTES", "HOURS", "DAYS"]
      }
    },
    mounted() {
      this.$http.get('/dsl/validators', {}).then(response => {
        this.type = response.data.sort();
      }, response => {
        this.viewError = true;
        this.msgError = "Error during call service";
      });
    },
    computed: {
      formTitle() {
        return this.editedIndex === -1 ? 'New Item' : 'Edit Item';
      }
    },
    methods: {
      isMandatory() {
        return this.editedItem.typeValidation == "MANDATORY_FIELD";
      },
      isBlackList() {
        return this.editedItem.typeValidation == "BLACK_LIST_FIELD";
      },
      isMaxField() {
        return this.editedItem.typeValidation == "MAX_FIELD";
      },
      isMaxMessageSize() {
        return this.editedItem.typeValidation == "MAX_MESSAGE_SIZE";
      },
      isFieldExist() {
        return this.editedItem.typeValidation == "FIELD_EXIST";
      },
      isTimestampValidation() {
        return this.editedItem.typeValidation == "TIMESTAMP_VALIDATION";
      },
      close() {
        this.dialog = false;
        this.editedItem = _.cloneDeep(this.defaultItem);
        this.editedIndex = -1;
      },
      editItem(item) {
        this.editedIndex = this.processValidations.indexOf(item);
        this.editedItem = _.cloneDeep(item);
        this.dialog = true;
      },
      deleteItem(item) {
        var index = this.processValidations.indexOf(item);
        confirm('Are you sure you want to delete this item?') && this.processValidations.splice(index, 1);
      },

      save() {
        if (this.editedIndex > -1) {
          Object.assign(this.processValidations[this.editedIndex], this.editedItem);
        } else {
          this.processValidations.push(this.editedItem);
        }
        this.close();
      },
      addItemBlackList() {
        this.editedItem.parameterValidation.blackList.push({key: this.keyBlackList, value: this.valueBlackList});
        this.keyBlackList = '';
        this.valueBlackList = '';
      },
      removeBlackList(item) {
        this.editedItem.parameterValidation.blackList = this.editedItem.parameterValidation.blackList.filter(e => e !== item);
        this.keyBlackList = '';
        this.valueBlackList = '';
      }
    }
  }
</script>
