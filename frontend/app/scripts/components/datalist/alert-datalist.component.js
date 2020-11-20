(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('alertDatalist', {
            controller: function($uibModal, AlertingSrv, CaseTemplateSrv, NotificationSrv) {
                var self = this;

                // this.updateField = function(newValue) {
                //     this.onUpdate({
                //         fieldName: ['customFields', this.field.reference, this.field.type].join('.'),
                //         value: newValue
                //     });
                // };
                //

                this.preview = function(event) {
                    $uibModal.open({
                        templateUrl: 'views/partials/alert/event.dialog.html',
                        controller: 'AlertEventCtrl',
                        controllerAs: 'dialog',
                        size: 'max',
                        resolve: {
                            event: function() {
                                return AlertingSrv.get(event._id);
                            },
                            templates: function() {
                                return CaseTemplateSrv.list();
                            },
                            readonly: true
                        }
                    })
                    .result
                    .then(function(/*response*/) {
                      self.data.update();
                    })
                    .catch(function(err) {
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('AlertPreview', err.data, err.status);
                        }

                    });
                };

                this.search = function () {
                    self.load();
                    if(self.onReload) {
                        self.reload();
                    }

                    //self.filtering.storeContext();
                };
                this.addFilterValue = function (field, value) {
                    this.filtering.addFilterValue(field, value);
                    this.search();
                };

            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/datalist/alert.datalist.component.html',
            bindings: {
                data: '<',
                filtering: '=',
                onReload: '&'
            }
        });
})();
