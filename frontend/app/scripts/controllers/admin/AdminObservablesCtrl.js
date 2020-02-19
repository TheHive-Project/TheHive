(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminObservablesCtrl',
        function(ModalUtilsSrv, NotificationSrv, ObservableTypeSrv, types) {
            var self = this;

            self.dataTypeList = types;
            self.params = {
                newDataType: null
            };

            self.load = function() {
                ObservableTypeSrv.list()
                    .then(function(response) {
                        self.dataTypeList = response.data;
                    })
                    .catch(function(response) {
                        NotificationSrv.error('AdminObservablesCtrl', response.data, response.status);
                    });
            };

            self.addArtifactDataTypeList = function() {
                ObservableTypeSrv.create({
                    name: self.params.newDataType
                }).then(function(/*response*/) {
                    NotificationSrv.log('Observable type created successfully', 'success');
                    self.load();
                }).catch(function(response) {
                    NotificationSrv.error('AdminObservablesCtrl', response.data, response.status);
                });

                self.params.newDataType = '';
            };

            self.deleteArtifactDataType = function(type) {
                ModalUtilsSrv.confirm('Remove observable type', s.sprintf('Are your sure your want to remove the observable type <strong>%s</strong>', type.name), {
                    okText: 'Yes, remove it',
                    flavor: 'danger',
                    isHtml: true
                })
                    .then(ObservableTypeSrv.remove(type._id))
                    .then(function(/*response*/) {
                        NotificationSrv.log('Observable type removed successfully', 'success');
                        self.load();
                    })
                    .catch(function(err){
                        if (err && !_.isString(err)) {
                            NotificationSrv.error('AdminObservablesCtrl', err.data, err.status);
                        }
                    });
            };
        });

})();
